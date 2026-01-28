package com.basic_chat.chat_service.service;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class SessionManager {
    // Sesiones autenticadas.
    private final Map<String, SessionInfo> authenticatedSessions = new ConcurrentHashMap<>();
    
    // Sessiones pendientes a autenticar.
    private final Map<String, Long> pendingAuthentication = new ConcurrentHashMap<>();

    // Usuarios online 
    private final Map<String, String> usersOnline = new ConcurrentHashMap<>();

    // ventana de tiempo para la autencicacion
    private static final long AUTH_TIMEOUT_MS = 5000; // 5 segs

    /**
     * Registra una nueva sesión cuando un usuario se conecta
     */
    public void  registerPendingConnection(String sessionId){
        log.debug("Concexion pendiente de autenticacion: {} añadida a la lista", sessionId);
        pendingAuthentication.put(sessionId, System.currentTimeMillis());
        
    }

    // Completar la autenticacion de una sesion.
    public void authenticateSession(String sessionId, String userId, String username, WebSocketSession wSession){
 
        log.debug("Removiendo la sesion {} de pendientes", sessionId);
        pendingAuthentication.remove(sessionId);

        
        SessionInfo info = new SessionInfo(userId, username, wSession);
        log.debug("Añadiendo la sesion: {} a sesiones autenticadas", sessionId);
        authenticatedSessions.put(sessionId, info);

        // Agregar a usuarios en linea (toma el username que viene en el token)
        log.debug("Añadiendo al usuario {} a la lista de usuarios online", username);
        usersOnline.put(username, sessionId);

        log.info("Sesion autenticada: {} - Usuario: {} ({})", sessionId, username);
    }

    // Verifica si una sesion esta autenticada.
    public boolean isAuthenticated(String sessionId){
        return authenticatedSessions.containsKey(sessionId);
    }

    // Verifica si una sesion esta pendiente de autenticacion
    public boolean isPendingAuthentication(String sessionId){
        return pendingAuthentication.containsKey(sessionId);
    }

    // Verifica si la ventana para autenticacion expiro
    public boolean hasAuthenticationExpired(String sessionId){
        Long connectionTime = pendingAuthentication.get(sessionId);
        if(connectionTime == null){
            return false;
        }
        long elapsed = System.currentTimeMillis() - connectionTime;
        return elapsed > AUTH_TIMEOUT_MS;
    }

     /**
     * Remueve una sesión autenticada o pendiente
     */
    public void removeSession(String sessionId) {
        SessionInfo removed = authenticatedSessions.remove(sessionId);
        pendingAuthentication.remove(sessionId);

        if (removed != null) {
           // Remover también de usersOnline
           usersOnline.remove(removed.getUsername());
           log.info("Sesion removida: {} - Usuario: {}", sessionId, removed.getUsername());
        }else{
            log.debug("Sesion pendiente removida: {}", sessionId);
        }
    }

    public SessionInfo getSessionInfo(String sessionId){
        return authenticatedSessions.get(sessionId);
    }

    // buscar sesion por username
    public SessionInfo findByUsername(String username){
        String sessionId = usersOnline.get(username);
        if(sessionId == null){
            return null;
        }
        return authenticatedSessions.get(sessionId);
    }

    // Verifica si un usuario se halla en linea
    public boolean isUserOnline(String userId){
        log.debug("Verificando si {} se halla en linea", userId);
        if(usersOnline.containsKey(userId)){
            return true;
        }else{
            log.debug("El usuario: {} no se halla en linea", userId);
            return false;
        }      
    }

    @Data
    @AllArgsConstructor
    public static class SessionInfo{
        private String userId;
        private String username;
        private WebSocketSession wsSession;
    }

}
