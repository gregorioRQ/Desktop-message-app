package com.basic_chat.chat_service.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de sesiones WebSocket locales.
 * 
 * Mantiene un mapeo en memoria de sessionId -> WebSocketSession.
 * 
 * IMPORTANTE ARQUITECTURA:
 * El objeto WebSocketSession representa una conexión TCP física y NO es serializable.
 * Por tanto, NO se puede guardar en Redis. Debe residir en la memoria RAM del servidor.
 * 
 * La verificación de usuarios online y el mapeo de userId/username -> sessionId
 * es responsabilidad de Redis (gestionado por el API Gateway).
 * Este servicio solo gestiona las sesiones WebSocket locales para envío de mensajes.
 */
@Service
@Slf4j
public class SessionManager {
    // Sesiones WebSocket locales: sessionId -> SessionInfo
    private final Map<String, SessionInfo> localSessions = new ConcurrentHashMap<>();

    /**
     * Registra una sesión WebSocket cuando un usuario autenticado se conecta.
     * 
     * @param sessionId ID de la sesión WebSocket
     * @param userId ID del usuario (para referencia)
     * @param username Username del usuario (para referencia)
     * @param wSession Sesión WebSocket
     */
    public void registerSession(String sessionId, String userId, String username, WebSocketSession wSession) {
        SessionInfo info = new SessionInfo(userId, username, wSession);
        localSessions.put(sessionId, info);
        log.info("Sesión WebSocket registrada: {} - Usuario: {} ({})", sessionId, username, userId);
    }

    /**
     * Remueve una sesión WebSocket cuando el usuario se desconecta.
     */
    public void removeSession(String sessionId) {
        SessionInfo removed = localSessions.remove(sessionId);
        if (removed != null) {
            log.info("Sesión WebSocket removida: {} - Usuario: {}", sessionId, removed.getUsername());
        }
    }

    /**
     * Obtiene la información de una sesión WebSocket por su sessionId.
     */
    public SessionInfo getSessionInfo(String sessionId) {
        return localSessions.get(sessionId);
    }

    @Data
    @AllArgsConstructor
    public static class SessionInfo {
        private String userId;
        private String username;
        private WebSocketSession wsSession;
    }

}
