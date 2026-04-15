package com.basic_chat.connection_service.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SessionRegistryService {

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;

    @Value("${notification.service.url:http://localhost:8084}")
    private String notificationServiceUrl;

    private static final String SESSION_USER_PREFIX = "session:";
    private static final String SESSION_USER_SUFFIX = ":user";
    private static final String USER_NAME_PREFIX = "user:name:";
    private static final String USER_INSTANCE_PREFIX = "user:";
    private static final String USER_INSTANCE_SUFFIX = ":connectionInstance";
    private static final String INSTANCE_SESSIONS_PREFIX = "connection:instance:";
    private static final String INSTANCE_SESSIONS_SUFFIX = ":sessions";
    private static final String USER_SESSION_ID_PREFIX = "user:";
    private static final String USER_SESSION_ID_SUFFIX = ":sessionId";

    private final Map<String, SessionInfo> localSessions = new ConcurrentHashMap<>();

    public SessionRegistryService(StringRedisTemplate redisTemplate,
                                  @Value("${connection.service.instance.id}") String instanceId) {
        this.redisTemplate = redisTemplate;
        this.instanceId = instanceId;
        this.restTemplate = new RestTemplate();
    }

    /**
     * Registra una nueva sesión de WebSocket para un usuario.
     * 
     * Este método almacena la información de la sesión en Redis y en memoria local.
     * También notifica al notification-service sobre el estado ONLINE del usuario.
     * 
     * Nota: La lista de contactos online ahora se envía automáticamente 
     * por el notification-service vía SSE cuando el usuario se conecta.
     * No es necesario enviarla desde connection-service.
     * 
     * @param sessionId ID único de la sesión WebSocket
     * @param userId ID del usuario
     * @param username Nombre de usuario
     * @param session Objeto WebSocketSession
     */
    public void registerSession(String sessionId, String userId, String username, WebSocketSession session) {
        SessionInfo existingSession = localSessions.get(userId);
        if (existingSession != null) {
            log.warn("Usuario {} ya tiene una sesión activa. Reemplazando.", userId);
            String oldSessionId = existingSession.getSession().getId();
            cleanupSessionFromRedis(userId, oldSessionId);
        }

        localSessions.put(userId, new SessionInfo(userId, username, session));

        redisTemplate.opsForValue().set(SESSION_USER_PREFIX + sessionId + SESSION_USER_SUFFIX, userId);
        redisTemplate.opsForValue().set("session:" + sessionId + ":username", username);
        redisTemplate.opsForValue().set(USER_NAME_PREFIX + username, userId);
        redisTemplate.opsForValue().set(USER_INSTANCE_PREFIX + userId + USER_INSTANCE_SUFFIX, instanceId);
        redisTemplate.opsForValue().set(USER_SESSION_ID_PREFIX + userId + USER_SESSION_ID_SUFFIX, sessionId);
        redisTemplate.opsForList().rightPush(INSTANCE_SESSIONS_PREFIX + instanceId + INSTANCE_SESSIONS_SUFFIX, sessionId);

        log.info("Sesión registrada en instance {} - sessionId: {}, userId: {}, username: {}",
            instanceId, sessionId, userId, username);

        notifyPresenceChange(userId, username, "ONLINE");
    }

    /**
     * Notifica al servicio de notificaciones sobre el cambio de presencia.
     * 
     * Este método envía una petición HTTP al notification-service para informar
     * que un usuario se ha conectado (ONLINE) o desconectado (OFFLINE).
     * 
     * Nota: El notification-service se encarga de notificar a los contactos
     * sobre estos cambios de presencia vía SSE.
     * 
     * @param userId ID del usuario
     * @param username Nombre de usuario
     * @param type "ONLINE" o "OFFLINE"
     */
    private void notifyPresenceChange(String userId, String username, String type) {
        try {
            String endpoint = notificationServiceUrl + (type.equals("ONLINE") ? "/api/presence/online" : "/api/presence/offline");
            String jsonBody = String.format("{\"userId\":\"%s\",\"username\":\"%s\"}", userId, username);

            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<String> request = new org.springframework.http.HttpEntity<>(jsonBody, headers);

            restTemplate.postForObject(endpoint, request, String.class);
            log.info("Notificación de presencia enviada a NS: {} para usuario: {}", type, userId);
        } catch (Exception e) {
            log.warn("Error notifying presence change to NS: {}. Continuing anyway.", e.getMessage());
        }
    }

    /**
     * Remueve una sesión WebSocket.
     * 
     * Este método se llama cuando un usuario se desconecta. Limpia la sesión
     * de Redis y memoria local, y notifica al notification-service.
     * 
     * @param sessionId ID de la sesión a remover
     */
    public void removeSession(String sessionId) {
        SessionInfo infoToRemove = null;
        String userIdToRemove = null;

        for (Map.Entry<String, SessionInfo> entry : localSessions.entrySet()) {
            if (entry.getValue().getSession().getId().equals(sessionId)) {
                infoToRemove = entry.getValue();
                userIdToRemove = entry.getKey();
                break;
            }
        }

        if (userIdToRemove != null) {
            localSessions.remove(userIdToRemove);
            String username = infoToRemove.getUsername();
            cleanupSessionFromRedis(userIdToRemove, sessionId);

            log.info("Sesión removida de instance {} - sessionId: {}, userId: {}",
                instanceId, sessionId, userIdToRemove);

            notifyPresenceChange(userIdToRemove, username, "OFFLINE");
        } else {
            log.warn("Sesión {} no encontrada en el mapa local", sessionId);
        }
    }

    private void cleanupSessionFromRedis(String userId, String sessionId) {
        redisTemplate.delete(SESSION_USER_PREFIX + sessionId + SESSION_USER_SUFFIX);
        redisTemplate.delete("session:" + sessionId + ":username");
        redisTemplate.delete(USER_INSTANCE_PREFIX + userId + USER_INSTANCE_SUFFIX);
        redisTemplate.delete(USER_SESSION_ID_PREFIX + userId + USER_SESSION_ID_SUFFIX);
        redisTemplate.opsForList().remove(INSTANCE_SESSIONS_PREFIX + instanceId + INSTANCE_SESSIONS_SUFFIX, 1, sessionId);
    }

    public SessionInfo getSessionByUserId(String userId) {
        return localSessions.get(userId);
    }

    public SessionInfo getSession(String sessionId) {
        for (SessionInfo info : localSessions.values()) {
            if (info.getSession().getId().equals(sessionId)) {
                return info;
            }
        }
        return null;
    }

    public String getUserIdBySession(String sessionId) {
        return redisTemplate.opsForValue().get(SESSION_USER_PREFIX + sessionId + SESSION_USER_SUFFIX);
    }

    public String getUsernameBySession(String sessionId) {
        return redisTemplate.opsForValue().get("session:" + sessionId + ":username");
    }

    public String getUserIdByUsername(String username) {
        return redisTemplate.opsForValue().get(USER_NAME_PREFIX + username);
    }

    public String getConnectionInstance(String userId) {
        return redisTemplate.opsForValue().get(USER_INSTANCE_PREFIX + userId + USER_INSTANCE_SUFFIX);
    }

    public boolean isUserOnline(String userId) {
        return getConnectionInstance(userId) != null;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void sendToSession(String sessionId, byte[] data) {
        SessionInfo info = null;
        for (SessionInfo sessionInfo : localSessions.values()) {
            if (sessionInfo.getSession().getId().equals(sessionId)) {
                info = sessionInfo;
                break;
            }
        }

        if (info != null && info.getSession().isOpen()) {
            try {
                info.getSession().sendMessage(new org.springframework.web.socket.BinaryMessage(data));
            } catch (Exception e) {
                log.error("Error sending to session {}: {}", sessionId, e.getMessage());
            }
        }
    }

    public void sendToUser(String userId, byte[] data) {
        String instance = getConnectionInstance(userId);
        if (instance == null) {
            return;
        }

        if (instance.equals(instanceId)) {
            SessionInfo sessionInfo = localSessions.get(userId);
            if (sessionInfo != null && sessionInfo.getSession().isOpen()) {
                try {
                    sessionInfo.getSession().sendMessage(new org.springframework.web.socket.BinaryMessage(data));
                } catch (Exception e) {
                    log.error("Error sending to user {}: {}", userId, e.getMessage());
                }
            }
        }
    }

    public void sendToUserByUsername(String username, byte[] data) {
        String userId = redisTemplate.opsForValue().get(USER_NAME_PREFIX + username);
        if (userId != null) {
            sendToUser(userId, data);
        }
    }

    public static class SessionInfo {
        private final String userId;
        private final String username;
        private final WebSocketSession session;

        public SessionInfo(String userId, String username, WebSocketSession session) {
            this.userId = userId;
            this.username = username;
            this.session = session;
        }

        public String getUserId() { return userId; }
        public String getUsername() { return username; }
        public WebSocketSession getSession() { return session; }
    }
}
