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
        pendingAuthentication.put(sessionId, System.currentTimeMillis());
        log.debug("Concexion pendiente de autenticacion: {}", sessionId);
    }

    // Completar la autenticacion de una sesion.
    public void authenticateSession(String sessionId, String userId, String username, WebSocketSession wSession){
        // Remover de pendientes
        pendingAuthentication.remove(sessionId);

        // Agregar a autenticadas
        SessionInfo info = new SessionInfo(userId, username, wSession);
        authenticatedSessions.put(sessionId, info);

        // Agregar a usuarios en linea (toma el username que viene en el token)
        // FIX: Guardar username -> sessionId (no username -> username)
        System.out.println("Añadiendo usuario a la lista online: " + username + " sessionId: " + sessionId);
        usersOnline.put(username, sessionId);

        log.info("Sesion autenticada: {} - Usuario: {} ({})", sessionId, username);
    }

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


    public boolean isUserOnline(String userId){
        System.out.println("Verificando si el usuario: " + userId + " esta en linea");
        return usersOnline.containsKey(userId);
    }

    @Data
    @AllArgsConstructor
    public static class SessionInfo{
        private String userId;
        private String username;
        private WebSocketSession wsSession;
    }

}
