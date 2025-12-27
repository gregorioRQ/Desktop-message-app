package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.security.JwtValidator;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.chat_service.service.SessionManager.SessionInfo;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.AuthMessage;
import com.basic_chat.proto.MessagesProto.AuthResponse;
import com.basic_chat.proto.MessagesProto.ChatMessage;
import com.basic_chat.proto.MessagesProto.DeleteMessageRequest;
import com.basic_chat.proto.MessagesProto.MessageType;
import com.basic_chat.proto.MessagesProto.WsMessage;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Handler WebSocket que integra SessionManager y MessageService
 * Su propósito es guardar la sesion en memoria, por ahora usar este enfoque
 * ya que es mas sencillo de implementar.
 */
@Component
@Slf4j
public class MyBinaryWebSocketHandler extends AbstractWebSocketHandler {

    private static final DateTimeFormatter formatter = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final SessionManager sessionManager;
    private final MessageService messageService;
    private final JwtValidator jwtValidator;

    public MyBinaryWebSocketHandler(SessionManager sessionManager, MessageService messageService, JwtValidator jwtValidator) {
        this.sessionManager = sessionManager;
        this.messageService = messageService;
        this.jwtValidator = jwtValidator;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("\n ** Nueva conexión WebSocket: " + session.getId());
        
        sessionManager.registerPendingConnection(session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        try {
            // Deserializar mensaje Protobuf
            String sessionId = session.getId();
            byte[] payload = message.getPayload().array();
            MessagesProto.WsMessage mensajeRecibido = MessagesProto.WsMessage.parseFrom(payload);

            // verificar si el mensaje es de autenticacion
            if(mensajeRecibido.hasAuthMessage()){
                log.info("Mensaje de autenticación recibido");
                handleAuthentication(session, mensajeRecibido.getAuthMessage());
                return;
            }

            // verificar si esta autenticado
            if(!sessionManager.isAuthenticated(sessionId)){
                //Si esta pendiente y no ha expirado, recordar que debe autenticarse
                if(sessionManager.isPendingAuthentication(sessionId)){
                    // verifica si la ventana de auth expiro.
                    if(sessionManager.hasAuthenticationExpired(sessionId)){
                        log.warn("Tiempo de autenticacion expirado para la sesion: {}", sessionId);
                        sendAuthError(session, "Tiempo de autenticacion expirado");
                        session.close(CloseStatus.POLICY_VIOLATION);
                        sessionManager.removeSession(sessionId);
                        return;
                    }
                    // Aun  queda tiempo para autenticarse.
                    sendAuthError(session, "La ventana para autenticarse aun no expiro");
                    return;
                }

                // No esta ni autenticado ni pendiente
                log.warn("Intento de enviar mensaje sin autenticacion: {}", sessionId);
                sendAuthError(session, "No autenticado");
                session.close();
                return;
            }

            // procesar mensaje normal
            if(mensajeRecibido.hasChatMessage()){
                handleChatMessage(session, message, mensajeRecibido.getChatMessage().getRecipient());
            }

            if(mensajeRecibido.hasDeleteMessageRequest()){
                handleDeleteMessage(mensajeRecibido.getDeleteMessageRequest());
            }

        } catch (Exception e) {
            System.err.println("Error procesando mensaje: " + e.getMessage());
            e.printStackTrace();
            try{
                session.close();
            }catch(IOException ex){
                log.error("Error al cerrar la sesion", ex);
            }
            
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // Opcionalmente manejar mensajes de texto (como comandos)
        System.out.println("Mensaje de texto recibido: " + message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // Remover la sesión del SessionManager
        sessionManager.removeSession(session.getId());
        
        System.out.println("\n✗ Conexión cerrada: " + session.getId());
        System.out.println("  Razón: " + status);
  
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("\n✗ Error de transporte: " + session.getId());
        System.err.println("  Error: " + exception.getMessage());
    }

    private void handleAuthentication(WebSocketSession session, AuthMessage authMessage){

        String sessionId = session.getId();
        try{

            String token = authMessage.getToken();

            if(token == null || token.isEmpty()){
                log.warn("Token vacio recibido de sesion: {}", sessionId);
                sendAuthError(session, "Token requerido");
                return;
            }

            Claims claims = jwtValidator.validateToken(token);
            String userId = jwtValidator.getUserId(claims);
            String username = jwtValidator.getUsername(claims);
            System.out.println("***** USERNAME: " + username);
            System.out.println("******** USER_ID: " + userId);

            // registrar sesión
            sessionManager.authenticateSession(sessionId, userId, username, session);

            AuthResponse response = AuthResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Autenticación exitosa")
                .setUserId(userId)
                .setUsername(username) 
                .build();

            WsMessage wsResponse = WsMessage.newBuilder()
                .setAuthResponse(response)
                .build();

            session.sendMessage(new BinaryMessage(wsResponse.toByteArray()));
            log.info("Usuario autenticado: {} ({})", username, userId);
        }catch(Exception e){
            sendAuthError(session, "Token inválido o expirado");
            try {
                session.close();
            } catch (IOException ex) {
               
            }
        }
    }

    /**
     * Maneja un mensaje de CHAT
     */
    private void handleChatMessage(WebSocketSession session, BinaryMessage mensaje, String recipient) {
        // Delegar al MessageService para:
        // 1. Intentar entrega directa si destinatario está online
        // 2. Persistir en DB
        // 3. Enviar notificación push si está offline
        //messageService.processMessage(mensaje, session.getId());
        System.out.println("Nuevo mensaje para: " + recipient);
        
        // Obtener la sesión del destinatario
        SessionManager.SessionInfo recipientSession = sessionManager.findByUsername(recipient);
        
        if(sessionManager.isUserOnline(recipient) && recipientSession != null){
            try {
                // Enviar el mensaje al DESTINATARIO, no al remitente
                recipientSession.getWsSession().sendMessage(mensaje);
                System.out.println("Mensaje entregado a: " + recipient);
            } catch (Exception e) {
                System.err.println("Error enviando el mensaje: " + e.getMessage());
                // Si falla, guardar el mensaje para entrega posterior
                try {
                    byte[] payload = mensaje.getPayload().array();
                    MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.parseFrom(payload);
                    MessagesProto.ChatMessage chatMessage = wsMessage.getChatMessage();
                    messageService.saveMessage(chatMessage);
                } catch (Exception ex) {
                    System.err.println("Error al guardar el mensaje: " + ex.getMessage());
                }
            }
        }else{
            System.out.println("El usuario: " + recipient + " no se halla en linea");
            // Guardar el mensaje para entrega posterior
            try {
                byte[] payload = mensaje.getPayload().array();
                MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.parseFrom(payload);
                MessagesProto.ChatMessage chatMessage = wsMessage.getChatMessage();
                messageService.saveMessage(chatMessage);
                System.out.println("Mensaje guardado en BD para entrega posterior");
            } catch (Exception ex) {
                System.err.println("Error al guardar el mensaje: " + ex.getMessage());
            }
        }
        
        // Enviar ACK al remitente
        //sendAck(session, mensaje);
    }

    private void handleDeleteMessage(DeleteMessageRequest request){
        messageService.deleteMessage(request.getMessageId(), request.getSenderUsername());
    }

    /**
     * Maneja una ALERTA o notificación del sistema
     */
    private void handleAlert(WebSocketSession session, MessagesProto.ChatMessage mensaje) {
        System.out.println("→ Alerta recibida: " + mensaje.getContent());
        // Procesar alerta según lógica de negocio
    }

    /**
     * Envía un ACK (confirmación) al cliente
     */
    private void sendAck(WebSocketSession session, MessagesProto originalMessage) {
        try {
            ChatMessage ack = ChatMessage.newBuilder()
                    .setSender("SERVIDOR")
                    .setContent("ACK: Mensaje recibido")
                    .setTimestamp(System.currentTimeMillis())
                    .setType(MessageType.DELIVERY_RECEIPT)
                    .build();
            
            session.sendMessage(new BinaryMessage(Objects.requireNonNull(ack.toByteArray())));
        } catch (Exception e) {
            System.err.println("Error enviando ACK: " + e.getMessage());
        }
    }

    /**
     * Envía un mensaje de error al cliente
     */
    private void sendAuthError(WebSocketSession session, String errorMessage) {
        try {
            AuthResponse response = AuthResponse.newBuilder()
                .setSuccess(false)
                .setMessage(errorMessage)
                .build();

            WsMessage wsResponse = WsMessage.newBuilder()
                .setAuthResponse(response)
                .build();
            
            session.sendMessage(new BinaryMessage(Objects.requireNonNull(wsResponse.toByteArray())));
        } catch (Exception e) {
            System.err.println("Error enviando mensaje de error: " + e.getMessage());
        }
    }

    /**
     * Notifica a otros usuarios sobre el cambio de estado (online/offline)
     
    private void notifyUserStatus(String userId, boolean isOnline) {
        String status = isOnline ? "online" : "offline";
        
        ChatMessage notification = ChatMessage.newBuilder()
                .setSender("SISTEMA")
                .setContent(userId + " está " + status)
                .setTimestamp(System.currentTimeMillis())
                .setType(MessageType.ALERT)
                .build();
        
        // Broadcast a todos excepto al usuario que cambió de estado
        sessionManager.getAllSessions().forEach((otherUserId, otherSession) -> {
            if (!otherUserId.equals(userId) && otherSession.isOpen()) {
                try {
                    otherSession.sendMessage(new BinaryMessage(Objects.requireNonNull(notification.toByteArray())));
                } catch (Exception e) {
                    System.err.println("Error notificando status a " + otherUserId);
                }
            }
        });
    }*/
}
