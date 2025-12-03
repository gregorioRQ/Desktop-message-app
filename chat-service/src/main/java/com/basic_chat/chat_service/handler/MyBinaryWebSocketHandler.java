package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.ChatMessage;
import com.basic_chat.proto.MessagesProto.LoginResponse;
import com.basic_chat.proto.MessagesProto.MessageType;
import com.basic_chat.proto.MessagesProto.WsMessage;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Handler WebSocket que integra SessionManager y MessageService
 * Su propósito es guardar la sesion en memoria, por ahora usar este enfoque
 * ya que es mas sencillo de implementar.
 */
@Component
public class MyBinaryWebSocketHandler extends AbstractWebSocketHandler {

    private static final DateTimeFormatter formatter = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final SessionManager sessionManager;
    private final MessageService messageService;

    public MyBinaryWebSocketHandler(SessionManager sessionManager, MessageService messageService) {
        this.sessionManager = sessionManager;
        this.messageService = messageService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        System.out.println("\n✓ Nueva conexión WebSocket: " + session.getId());
        
        // Nota: El userId se establecerá cuando el cliente envíe su primer mensaje LOGIN
        // Por ahora solo registramos la sesión
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        try {
            // Deserializar mensaje Protobuf
            byte[] payload = message.getPayload().array();
            MessagesProto.WsMessage mensajeRecibido = MessagesProto.WsMessage.parseFrom(payload);

            // Formatear timestamp para log
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(mensajeRecibido.getChatMessage().getTimestamp()),
                ZoneId.systemDefault()
            );

            System.out.println("\n=== Mensaje Recibido ===");
            System.out.println("Sesión: " + session.getId());
            System.out.println("Usuario ID: " + mensajeRecibido.getChatMessage().getSender());
            System.out.println("Contenido: " + mensajeRecibido.getChatMessage().getContent());
            System.out.println("Tipo: " + mensajeRecibido.getChatMessage().getType().name());
            System.out.println("Timestamp: " + dateTime.format(formatter));
            System.out.println("========================\n");

            // Procesar según el tipo de mensaje
            switch (mensajeRecibido.getChatMessage().getType()) {
                case LOGIN:
                    handleLogin(session, mensajeRecibido);
                    break;
                    
                case CHAT:
                    handleChatMessage(session, mensajeRecibido.getChatMessage());
                    break;
                    
                case ALERT:
                    handleAlert(session, mensajeRecibido.getChatMessage());
                    break;
                    
                default:
                    System.out.println("⚠ Tipo de mensaje desconocido: " + mensajeRecibido.getChatMessage().getType().name());
            }

        } catch (Exception e) {
            System.err.println("✗ Error procesando mensaje: " + e.getMessage());
            e.printStackTrace();
            
            // Enviar mensaje de error al cliente
            sendErrorToClient(session, e.getMessage());
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
        System.out.println("  Usuarios online: " + sessionManager.getOnlineUserCount());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("\n✗ Error de transporte: " + session.getId());
        System.err.println("  Error: " + exception.getMessage());
    }

    /* 
     * Maneja el LOGIN de un usuario
     */
    private void handleLogin(WebSocketSession session, WsMessage mensaje) {
        // se usa el username como si fuera el id.
        String userId = mensaje.getLoginRequest().getUsername();
        
        // Registrar la sesión en el SessionManager
        sessionManager.registerSession(userId, session);
        
        // Enviar mensajes pendientes (no entregados mientras estaba offline)
        //messageService.sendPendingMessages(userId);
        
        // Enviar confirmación de login
        LoginResponse respuesta = LoginResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Bienvenido " + userId + ". Tienes " + 
                              sessionManager.getOnlineUserCount() + " usuarios online.")
                .setUserId("SERVIDOR")
                .build();
        
        try {
            session.sendMessage(new BinaryMessage(Objects.requireNonNull(respuesta.toByteArray())));
        } catch (Exception e) {
            System.err.println("Error enviando confirmación de login: " + e.getMessage());
        }
        
        // Notificar a otros usuarios (opcional)
        //notifyUserStatus(userId, true);
    }

    /**
     * Maneja un mensaje de CHAT
     */
    private void handleChatMessage(WebSocketSession session, MessagesProto.ChatMessage mensaje) {
        // Delegar al MessageService para:
        // 1. Intentar entrega directa si destinatario está online
        // 2. Persistir en DB
        // 3. Enviar notificación push si está offline
        //messageService.processMessage(mensaje, session.getId());
        
        // Enviar ACK al remitente
        //sendAck(session, mensaje);
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
    private void sendErrorToClient(WebSocketSession session, String errorMessage) {
        try {
            ChatMessage error = ChatMessage.newBuilder()
                    .setSender("SISTEMA")
                    .setContent("ERROR: " + errorMessage)
                    .setTimestamp(System.currentTimeMillis())
                    .setType(MessageType.ALERT)
                    .build();
            
            session.sendMessage(new BinaryMessage(Objects.requireNonNull(error.toByteArray())));
        } catch (Exception e) {
            System.err.println("Error enviando mensaje de error: " + e.getMessage());
        }
    }

    /**
     * Notifica a otros usuarios sobre el cambio de estado (online/offline)
     */
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
    }
}
