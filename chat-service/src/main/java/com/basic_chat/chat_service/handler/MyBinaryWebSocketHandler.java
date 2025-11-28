package com.basic_chat.chat_service.handler;

import com.basic_chat.proto.PaqueteDatos;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handler para manejar mensajes WebSocket binarios con Protobuf
 */
@Component
public class MyBinaryWebSocketHandler extends BinaryWebSocketHandler {

    private static final DateTimeFormatter formatter = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    // Almacenar sesiones activas
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        System.out.println("\n✓ Nueva conexión establecida: " + session.getId());
        System.out.println("  Total de sesiones activas: " + sessions.size());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        try {
            // Deserializar el mensaje Protobuf
            byte[] payload = message.getPayload().array();
            PaqueteDatos mensajeRecibido = PaqueteDatos.parseFrom(payload);

            // Formatear timestamp
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

            // Crear respuesta
            PaqueteDatos respuesta = PaqueteDatos.newBuilder()
                    .setUsuarioId("SERVIDOR")
                    .setContenido("Mensaje recibido: " + mensajeRecibido.getContenido())
                    .setTimestamp(System.currentTimeMillis())
                    .setTipo(PaqueteDatos.Tipo.ALERTA)
                    .build();

            // Enviar respuesta al cliente que envió el mensaje
            session.sendMessage(new BinaryMessage(respuesta.toByteArray()));

            // Broadcast a todos los demás clientes (opcional)
            broadcastMessage(mensajeRecibido, session.getId());

        } catch (Exception e) {
            System.err.println("✗ Error procesando mensaje: " + e.getMessage());
            e.printStackTrace();
            
            // Enviar mensaje de error al cliente
            PaqueteDatos error = PaqueteDatos.newBuilder()
                    .setUsuarioId("SISTEMA")
                    .setContenido("Error: " + e.getMessage())
                    .setTimestamp(System.currentTimeMillis())
                    .setTipo(PaqueteDatos.Tipo.ALERTA)
                    .build();
            
            session.sendMessage(new BinaryMessage(error.toByteArray()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        System.out.println("\n✗ Conexión cerrada: " + session.getId());
        System.out.println("  Razón: " + status);
        System.out.println("  Sesiones activas restantes: " + sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("\n✗ Error de transporte en sesión: " + session.getId());
        System.err.println("  Error: " + exception.getMessage());
    }

    /**
     * Envía un mensaje a todos los clientes conectados excepto al remitente
     */
    private void broadcastMessage(PaqueteDatos mensaje, String excludeSessionId) {
        sessions.forEach((sessionId, session) -> {
            if (!sessionId.equals(excludeSessionId) && session.isOpen()) {
                try {
                    session.sendMessage(new BinaryMessage(mensaje.toByteArray()));
                } catch (IOException e) {
                    System.err.println("Error enviando broadcast a " + sessionId + ": " + e.getMessage());
                }
            }
        });
    }

    /**
     * Envía un mensaje a todos los clientes conectados
     */
    public void broadcastToAll(PaqueteDatos mensaje) {
        sessions.forEach((sessionId, session) -> {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new BinaryMessage(mensaje.toByteArray()));
                } catch (IOException e) {
                    System.err.println("Error enviando broadcast a " + sessionId + ": " + e.getMessage());
                }
            }
        });
    }
}