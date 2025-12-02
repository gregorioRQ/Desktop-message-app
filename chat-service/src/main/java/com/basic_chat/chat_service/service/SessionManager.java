package com.basic_chat.chat_service.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Servicio para gestionar sesiones WebSocket activas en memoria.
 * Mapea userId -> WebSocketSession para envío directo de mensajes.
 */
@Service
public class SessionManager {
    // userId -> WebSocketSession
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    
    // sessionId -> userId (para lookup inverso)
    private final Map<String, String> sessionToUser = new ConcurrentHashMap<>();

    /**
     * Registra una nueva sesión cuando un usuario se conecta
     */
    public void registerSession(String userId, WebSocketSession session) {
        // Remover sesión anterior si existe (usuario reconectándose)
        removeUserSession(userId);
        
        userSessions.put(userId, session);
        sessionToUser.put(session.getId(), userId);
        
        System.out.println("✓ Usuario registrado: " + userId + " (Session: " + session.getId() + ")");
        System.out.println("  Total usuarios online: " + userSessions.size());
    }

     /**
     * Remueve una sesión cuando se desconecta
     */
    public void removeSession(String sessionId) {
        String userId = sessionToUser.remove(sessionId);
        if (userId != null) {
            userSessions.remove(userId);
            System.out.println("✗ Usuario desconectado: " + userId);
            System.out.println("  Total usuarios online: " + userSessions.size());
        }
    }

    /**
     * Remueve todas las sesiones de un usuario
     */
    public void removeUserSession(String userId) {
        WebSocketSession oldSession = userSessions.remove(userId);
        if (oldSession != null) {
            sessionToUser.remove(oldSession.getId());
        }
    }

    /**
     * Verifica si un usuario está online
     */
    public boolean isUserOnline(String userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }

    /**
     * Obtiene la sesión de un usuario si está online
     */
    public Optional<WebSocketSession> getUserSession(String userId) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            return Optional.of(session);
        }
        return Optional.empty();
    }

    /**
     * Obtiene el userId asociado a una sesión
     */
    public Optional<String> getUserId(String sessionId) {
        return Optional.ofNullable(sessionToUser.get(sessionId));
    }

    /**
     * Obtiene lista de usuarios online
     */
    public Set<String> getOnlineUsers() {
        return userSessions.keySet().stream()
                .filter(this::isUserOnline)
                .collect(Collectors.toSet());
    }

    /**
     * Obtiene todas las sesiones activas (para broadcast)
     */
    public Map<String, WebSocketSession> getAllSessions() {
        return new ConcurrentHashMap<>(userSessions);
    }

    /**
     * Cuenta de usuarios online
     */
    public int getOnlineUserCount() {
        return (int) userSessions.values().stream()
                .filter(WebSocketSession::isOpen)
                .count();
    }

    /**
     * Limpia sesiones cerradas
     */
    public void cleanupClosedSessions() {
        userSessions.entrySet().removeIf(entry -> !entry.getValue().isOpen());
        sessionToUser.entrySet().removeIf(entry -> 
            !userSessions.containsKey(sessionToUser.get(entry.getKey())));
    }

}
