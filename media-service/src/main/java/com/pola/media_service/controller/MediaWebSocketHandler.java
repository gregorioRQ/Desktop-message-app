package com.pola.media_service.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import com.google.protobuf.InvalidProtocolBufferException;
import com.pola.media_service.proto.ImageAck;
import com.pola.media_service.proto.ImageMessage;
import com.pola.media_service.service.RedisSessionService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MediaWebSocketHandler extends BinaryWebSocketHandler{
 
    private final RedisSessionService redisSessionService;
    
    
    // Mapa de sesiones activas: userId -> WebSocketSession
    private final Map<String, WebSocketSession> activeSessions = new ConcurrentHashMap<>();

    public MediaWebSocketHandler(RedisSessionService redisSessionService){
        this.redisSessionService = redisSessionService;
    }
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Extraer userId y username de las cabeceras (inyectadas por el API Gateway)
        String userId = extractHeader(session, "X-User-ID");
        String username = extractHeader(session, "X-Username");

        if (userId == null || username == null || !isSessionValidInRedis(userId)) {
            log.warn("Cerrando sesión {}: cabeceras incompletas o sesión de Redis no válida. UserId: {}, Username: {}", 
                     session.getId(), userId, username);
            session.close(CloseStatus.POLICY_VIOLATION.withReason("User-ID/Username headers missing or invalid session"));
            return;
        }
        activeSessions.put(userId, session);
        log.info("WebSocket connected: userId={}, sessionId={}", userId, session.getId());
    }
    
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            byte[] payload = message.getPayload().array();
            
            ImageMessage imageMessage = ImageMessage.parseFrom(payload);
            
            log.info("Received image notification: mediaId={}, from={}, to={}", 
                imageMessage.getMediaId(),
                imageMessage.getSenderId(),
                imageMessage.getReceiverId());
            
            String receiverId = imageMessage.getReceiverId();
            WebSocketSession receiverSession = activeSessions.get(receiverId);
            
            if (receiverSession != null && receiverSession.isOpen()) {
                receiverSession.sendMessage(new BinaryMessage(payload));
                log.info("Image notification forwarded to receiver: {}", receiverId);
            } else {
                log.info("Receiver {} is offline, image notification will be queued", receiverId);
            }
            
        } catch (InvalidProtocolBufferException e) {
            log.error("Invalid protobuf message", e);
        } catch (IOException e) {
            log.error("Error sending message", e);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
         String userId = extractHeader(session, "X-User-ID");
        if (userId != null) {
            activeSessions.remove(userId);
            log.info("WebSocket disconnected: userId={}, status={}", userId, status);
            
            // TODO: Aquí podrías marcar mensajes como no entregados
        }
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket error for session {}: {}", session.getId(), exception.getMessage());
    }
   
    
  
    
    /**
     * Método público para enviar thumbnails pendientes cuando usuario se conecta
     */
    public void sendPendingThumbnails(Long userId) {
        WebSocketSession session = activeSessions.get(userId);
        if (session != null && session.isOpen()) {
            // TODO: Obtener thumbnails pendientes del service
            // mediaService.getPendingThumbnails(userId).forEach(thumbnail -> {
            //     try {
            //         session.sendMessage(new BinaryMessage(thumbnail.toByteArray()));
            //     } catch (IOException e) {
            //         log.error("Error sending pending thumbnail", e);
            //     }
            // });
        }
    }

     private String extractHeader(WebSocketSession session, String headerName) {
        try {
            String headerValue = session.getHandshakeHeaders().getFirst(headerName);
            return (headerValue != null && !headerValue.trim().isEmpty()) ? headerValue : null;
        } catch (Exception e) {
            log.error("Error al procesar cabecera {}", headerName, e);
            return null;
        }
    }

    private boolean isSessionValidInRedis(String userId) {
        if (!redisSessionService.hasSessionMapping(userId)) {
            log.warn("Conexión rechazada: No se encontró sesión en Redis para userId={}", userId);
            return false;
        }
        return true;
    }
}
