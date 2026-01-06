package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.security.JwtValidator;
import com.basic_chat.chat_service.service.AuthenticationGuard;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.chat_service.service.WsMessageDispatcher;
import com.basic_chat.chat_service.service.SessionManager.SessionInfo;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.AuthMessage;
import com.basic_chat.proto.MessagesProto.AuthResponse;
import com.basic_chat.proto.MessagesProto.ChatMessage;
import com.basic_chat.proto.MessagesProto.MessageType;
import com.basic_chat.proto.MessagesProto.UnreadMessagesList;
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
import java.util.List;
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
    private final AuthenticationGuard authenticationGuard;
    private final WsMessageDispatcher dispatcher;

    public MyBinaryWebSocketHandler(SessionManager sessionManager, MessageService messageService, JwtValidator jwtValidator, AuthenticationGuard authenticationGuard, WsMessageDispatcher dispatcher) {
        this.sessionManager = sessionManager;
        this.messageService = messageService;
        this.jwtValidator = jwtValidator;
        this.authenticationGuard = authenticationGuard;
        this.dispatcher = dispatcher;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("\n ** Nueva conexión WebSocket: " + session.getId());
        
        sessionManager.registerPendingConnection(session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        try {
            MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.parseFrom(message.getPayload().array());

            SessionContext context = new SessionContext(session, sessionManager);

            if(!wsMessage.hasAuthMessage()){
                authenticationGuard.check(context);
            }
            dispatcher.dispatch(context, wsMessage);
                if (wsMessage.hasAuthMessage() && sessionManager.isAuthenticated(session.getId())) {
                sendPendingMessages(session);
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
        System.err.println("\n Error de transporte: " + session.getId());
        System.err.println("  Error: " + exception.getMessage());
    }

    private void sendPendingMessages(WebSocketSession session){
        SessionManager.SessionInfo sessionInfo = sessionManager.getSessionInfo(session.getId());
        if(sessionInfo == null){
            return;
        }
        String username = sessionInfo.getUsername();
        List<ChatMessage> messages = messageService.getUnreadMessages(username);
        if(!messages.isEmpty()){
            UnreadMessagesList list = UnreadMessagesList.newBuilder().addAllMessages(messages).build();
            WsMessage wsMessage = WsMessage.newBuilder().setUnreadMessagesList(list).build();
            try {
                session.sendMessage(new BinaryMessage(wsMessage.toByteArray()));
            } catch (IOException e) {
                log.error("Error enviando mensajes pendientes", e);
            }
        }
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
