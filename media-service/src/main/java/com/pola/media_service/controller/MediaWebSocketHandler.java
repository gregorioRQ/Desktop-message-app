package com.pola.media_service.controller;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import com.google.protobuf.InvalidProtocolBufferException;
import com.pola.media_service.proto.ThumbnailAck;
import com.pola.media_service.proto.ThumbnailMessage;
import com.pola.media_service.service.MediaService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MediaWebSocketHandler extends BinaryWebSocketHandler{
    @Autowired
    private MediaService mediaService;
    
    // Mapa de sesiones activas: userId -> WebSocketSession
    private final Map<Long, WebSocketSession> activeSessions = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Extraer userId de los headers o query params
        Long userId = extractUserId(session);
        
        if (userId != null) {
            activeSessions.put(userId, session);
            log.info("WebSocket connected: userId={}, sessionId={}", userId, session.getId());
        } else {
            log.warn("Connection rejected: no userId found");
            session.close(CloseStatus.POLICY_VIOLATION);
        }
    }
    
    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        try {
            byte[] payload = message.getPayload().array();
            
            // Parsear mensaje Protobuf
            ThumbnailMessage thumbnailMessage = ThumbnailMessage.parseFrom(payload);
            
            log.info("Received thumbnail: mediaId={}, from={}, to={}", 
                thumbnailMessage.getMediaId(),
                thumbnailMessage.getSenderId(),
                thumbnailMessage.getReceiverId());
            
            // Buscar sesión del receptor
            Long receiverId = thumbnailMessage.getReceiverId();
            WebSocketSession receiverSession = activeSessions.get(receiverId);
            
            if (receiverSession != null && receiverSession.isOpen()) {
                // Receptor está ONLINE → enviar thumbnail directamente
                receiverSession.sendMessage(new BinaryMessage(payload));
                
                // Enviar ACK al emisor
                sendAck(session, thumbnailMessage.getMediaId(), true);
                
                // TODO: Marcar como delivered en BD
                mediaService.markAsDelivered(thumbnailMessage.getMediaId());
                
            } else {
                // Receptor está OFFLINE → guardar para enviar después
                log.info("Receiver {} is offline, thumbnail will be queued", receiverId);
                
                // TODO: Guardar en BD como pendiente (ya está guardado del upload HTTP)
                
                // Enviar ACK al emisor (guardado para envío posterior)
                sendAck(session, thumbnailMessage.getMediaId(), false);
            }
            
        } catch (InvalidProtocolBufferException e) {
            log.error("Invalid protobuf message", e);
        } catch (IOException e) {
            log.error("Error sending message", e);
        }
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = extractUserId(session);
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
     * Envía ACK al emisor confirmando recepción
     */
    private void sendAck(WebSocketSession session, String mediaId, boolean delivered) {
        try {
            ThumbnailAck ack = ThumbnailAck.newBuilder()
                .setMediaId(mediaId)
                .setReceived(true)
                .setTimestamp(System.currentTimeMillis())
                .build();
            
            session.sendMessage(new BinaryMessage(ack.toByteArray()));
            
        } catch (IOException e) {
            log.error("Error sending ACK", e);
        }
    }
    
    /**
     * Extrae userId de la sesión WebSocket
     * Puedes obtenerlo de: query params, headers, o atributos de sesión
     */
    private Long extractUserId(WebSocketSession session) {
        // Opción 1: Desde query params
        // ws://localhost:8080/ws/media?userId=123
        String query = session.getUri().getQuery();
        if (query != null && query.contains("userId=")) {
            String userIdStr = query.split("userId=")[1].split("&")[0];
            return Long.parseLong(userIdStr);
        }
        
        // Opción 2: Desde atributos de sesión (si usas interceptor)
        Object userIdAttr = session.getAttributes().get("userId");
        if (userIdAttr != null) {
            return (Long) userIdAttr;
        }
        
        return null;
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
}
