package com.basic_chat.chat_service.handler;

import com.basic_chat.proto.PaqueteDatos;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
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
            PaqueteDatos mensajeRecibido = PaqueteDatos.parseFrom(payload);

            // Formatear timestamp para log
            LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(mensajeRecibido.getTimestamp()),
                ZoneId.systemDefault()
            );

            System.out.println("\n=== Mensaje Recibido ===");
            System.out.println("Sesión: " + session.getId());
            System.out.println("Usuario ID: " + mensajeRecibido.getUsuarioId());
            System.out.println("Contenido: " + mensajeRecibido.getContenido());
            System.out.println("Tipo: " + mensajeRecibido.getTipo().name());
            System.out.println("Timestamp: " + dateTime.format(formatter));
            System.out.println("========================\n");

            // Procesar según el tipo de mensaje
            switch (mensajeRecibido.getTipo()) {
                case LOGIN:
                    handleLogin(session, mensajeRecibido);
                    break;
                    
                case CHAT:
                    handleChatMessage(session, mensajeRecibido);
                    break;
                    
                case ALERTA:
                    handleAlert(session, mensajeRecibido);
                    break;
                    
                default:
                    System.out.println("⚠ Tipo de mensaje desconocido: " + mensajeRecibido.getTipo());
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

    /**
     * Maneja el LOGIN de un usuario
     */
    private void handleLogin(WebSocketSession session, PaqueteDatos mensaje) {
        String userId = mensaje.getUsuarioId();
        
        // Registrar la sesión en el SessionManager
        sessionManager.registerSession(userId, session);
        
        // Enviar mensajes pendientes (no entregados mientras estaba offline)
        //messageService.sendPendingMessages(userId);
        
        // Enviar confirmación de login
        PaqueteDatos respuesta = PaqueteDatos.newBuilder()
                .setUsuarioId("SERVIDOR")
                .setContenido("Bienvenido " + userId + ". Tienes " + 
                              sessionManager.getOnlineUserCount() + " usuarios online.")
                .setTimestamp(System.currentTimeMillis())
                .setTipo(PaqueteDatos.Tipo.ALERTA)
                .build();
        
        try {
            session.sendMessage(new BinaryMessage(respuesta.toByteArray()));
        } catch (Exception e) {
            System.err.println("Error enviando confirmación de login: " + e.getMessage());
        }
        
        // Notificar a otros usuarios (opcional)
        notifyUserStatus(userId, true);
    }

    /**
     * Maneja un mensaje de CHAT
     */
    private void handleChatMessage(WebSocketSession session, PaqueteDatos mensaje) {
        // Delegar al MessageService para:
        // 1. Intentar entrega directa si destinatario está online
        // 2. Persistir en DB
        // 3. Enviar notificación push si está offline
        //messageService.processMessage(mensaje, session.getId());
        
        // Enviar ACK al remitente
        sendAck(session, mensaje);
    }

    /**
     * Maneja una ALERTA o notificación del sistema
     */
    private void handleAlert(WebSocketSession session, PaqueteDatos mensaje) {
        System.out.println("→ Alerta recibida: " + mensaje.getContenido());
        // Procesar alerta según lógica de negocio
    }

    /**
     * Envía un ACK (confirmación) al cliente
     */
    private void sendAck(WebSocketSession session, PaqueteDatos originalMessage) {
        try {
            PaqueteDatos ack = PaqueteDatos.newBuilder()
                    .setUsuarioId("SERVIDOR")
                    .setContenido("ACK: Mensaje recibido")
                    .setTimestamp(System.currentTimeMillis())
                    .setTipo(PaqueteDatos.Tipo.ALERTA)
                    .build();
            
            session.sendMessage(new BinaryMessage(ack.toByteArray()));
        } catch (Exception e) {
            System.err.println("Error enviando ACK: " + e.getMessage());
        }
    }

    /**
     * Envía un mensaje de error al cliente
     */
    private void sendErrorToClient(WebSocketSession session, String errorMessage) {
        try {
            PaqueteDatos error = PaqueteDatos.newBuilder()
                    .setUsuarioId("SISTEMA")
                    .setContenido("ERROR: " + errorMessage)
                    .setTimestamp(System.currentTimeMillis())
                    .setTipo(PaqueteDatos.Tipo.ALERTA)
                    .build();
            
            session.sendMessage(new BinaryMessage(error.toByteArray()));
        } catch (Exception e) {
            System.err.println("Error enviando mensaje de error: " + e.getMessage());
        }
    }

    /**
     * Notifica a otros usuarios sobre el cambio de estado (online/offline)
     */
    private void notifyUserStatus(String userId, boolean isOnline) {
        String status = isOnline ? "online" : "offline";
        
        PaqueteDatos notification = PaqueteDatos.newBuilder()
                .setUsuarioId("SISTEMA")
                .setContenido(userId + " está " + status)
                .setTimestamp(System.currentTimeMillis())
                .setTipo(PaqueteDatos.Tipo.ALERTA)
                .build();
        
        // Broadcast a todos excepto al usuario que cambió de estado
        sessionManager.getAllSessions().forEach((otherUserId, otherSession) -> {
            if (!otherUserId.equals(userId) && otherSession.isOpen()) {
                try {
                    otherSession.sendMessage(new BinaryMessage(notification.toByteArray()));
                } catch (Exception e) {
                    System.err.println("Error notificando status a " + otherUserId);
                }
            }
        });
    }
}