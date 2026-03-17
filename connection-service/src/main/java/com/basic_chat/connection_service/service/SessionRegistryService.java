package com.basic_chat.connection_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class SessionRegistryService {

    private final StringRedisTemplate redisTemplate;
    private final String instanceId;

    private static final String SESSION_USER_PREFIX = "session:";
    private static final String SESSION_USER_SUFFIX = ":user";
    private static final String USER_NAME_PREFIX = "user:name:";
    private static final String USER_INSTANCE_PREFIX = "user:";
    private static final String USER_INSTANCE_SUFFIX = ":connectionInstance";
    private static final String INSTANCE_SESSIONS_PREFIX = "connection:instance:";
    private static final String INSTANCE_SESSIONS_SUFFIX = ":sessions";
    private static final String USER_SESSION_ID_PREFIX = "user:";
    private static final String USER_SESSION_ID_SUFFIX = ":sessionId";

    /**
     * Mapa local de sesiones: userId -> SessionInfo
     * Almacena la sesión activa del usuario en esta instancia.
     * IMPORTANTE: Un usuario = Una sesión activa (simplificado de arquitectura anterior con listas)
     */
    private final Map<String, SessionInfo> localSessions = new ConcurrentHashMap<>();

    public SessionRegistryService(
            StringRedisTemplate redisTemplate,
            @Value("${connection.service.instance.id}") String instanceId) {
        this.redisTemplate = redisTemplate;
        this.instanceId = instanceId;
    }

    /**
     * Registra una nueva sesión de WebSocket para un usuario.
     * 
     * Este método:
     * 1. Verifica si el usuario ya tiene una sesión activa en esta instancia y la elimina
     * 2. Registra la nueva sesión en el mapa local (userId -> SessionInfo)
     * 3. Actualiza Redis con la información de la nueva sesión
     * 
     * @param sessionId ID de la sesión WebSocket
     * @param userId ID único del usuario
     * @param username Nombre de usuario
     * @param session Objeto WebSocketSession
     */
    public void registerSession(String sessionId, String userId, String username, WebSocketSession session) {
        // Verificar si el usuario ya tiene una sesión activa en esta instancia
        SessionInfo existingSession = localSessions.get(userId);
        if (existingSession != null) {
            log.warn("Usuario {} ya tiene una sesión activa en esta instancia. Reemplazando sesión anterior.", userId);
            // Limpiar la sesión anterior de Redis
            String oldSessionId = existingSession.getSession().getId();
            cleanupSessionFromRedis(userId, oldSessionId);
        }

        // Registrar nueva sesión en el mapa local
        localSessions.put(userId, new SessionInfo(userId, username, session));

        // Registrar en Redis (simplificado - una sola sesión por usuario)
        redisTemplate.opsForValue().set(SESSION_USER_PREFIX + sessionId + SESSION_USER_SUFFIX, userId);
        redisTemplate.opsForValue().set("session:" + sessionId + ":username", username);
        redisTemplate.opsForValue().set(USER_NAME_PREFIX + username, userId);
        redisTemplate.opsForValue().set(USER_INSTANCE_PREFIX + userId + USER_INSTANCE_SUFFIX, instanceId);
        redisTemplate.opsForValue().set(USER_SESSION_ID_PREFIX + userId + USER_SESSION_ID_SUFFIX, sessionId);
        redisTemplate.opsForList().rightPush(INSTANCE_SESSIONS_PREFIX + instanceId + INSTANCE_SESSIONS_SUFFIX, sessionId);

        log.info("Sesión registrada en instance {} - sessionId: {}, userId: {}, username: {}",
                instanceId, sessionId, userId, username);
    }

    /**
     * Elimina una sesión cuando el cliente se desconecta.
     * 
     * @param sessionId ID de la sesión WebSocket que se está removiendo
     */
    public void removeSession(String sessionId) {
        // Buscar la sesión por sessionId en el mapa local
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
            
            // Limpiar Redis
            cleanupSessionFromRedis(userIdToRemove, sessionId);

            log.info("Sesión removida de instance {} - sessionId: {}, userId: {}",
                    instanceId, sessionId, userIdToRemove);
        } else {
            log.warn("Sesión {} no encontrada en el mapa local de esta instancia", sessionId);
        }
    }

    /**
     * Limpia las claves de Redis relacionadas con una sesión.
     * 
     * @param userId ID del usuario
     * @param sessionId ID de la sesión
     */
    private void cleanupSessionFromRedis(String userId, String sessionId) {
        redisTemplate.delete(SESSION_USER_PREFIX + sessionId + SESSION_USER_SUFFIX);
        redisTemplate.delete("session:" + sessionId + ":username");
        redisTemplate.delete(USER_INSTANCE_PREFIX + userId + USER_INSTANCE_SUFFIX);
        redisTemplate.delete(USER_SESSION_ID_PREFIX + userId + USER_SESSION_ID_SUFFIX);
        redisTemplate.opsForList().remove(INSTANCE_SESSIONS_PREFIX + instanceId + INSTANCE_SESSIONS_SUFFIX, 1, sessionId);
        log.debug("Limpieza de Redis completada para userId: {}, sessionId: {}", userId, sessionId);
    }

    /**
     * Obtiene la información de la sesión por userId.
     * 
     * @param userId ID del usuario
     * @return SessionInfo o null si no existe
     */
    public SessionInfo getSessionByUserId(String userId) {
        return localSessions.get(userId);
    }

    /**
     * Obtiene la información de la sesión por sessionId.
     * 
     * @param sessionId ID de la sesión
     * @return SessionInfo o null si no existe
     */
    public SessionInfo getSession(String sessionId) {
        for (SessionInfo info : localSessions.values()) {
            if (info.getSession().getId().equals(sessionId)) {
                return info;
            }
        }
        return null;
    }

    /**
     * Obtiene el userId a partir del sessionId.
     * 
     * @param sessionId ID de la sesión
     * @return El userId asociado o null si no existe
     */
    public String getUserIdBySession(String sessionId) {
        return redisTemplate.opsForValue().get(SESSION_USER_PREFIX + sessionId + SESSION_USER_SUFFIX);
    }

    /**
     * Obtiene el username a partir del sessionId.
     * 
     * @param sessionId ID de la sesión
     * @return El username asociado o null si no existe
     */
    public String getUsernameBySession(String sessionId) {
        return redisTemplate.opsForValue().get("session:" + sessionId + ":username");
    }

    /**
     * Obtiene el userId a partir del username.
     * Utiliza el mapeo guardado en Redis: user:name:{username} -> userId
     * 
     * @param username Nombre de usuario (ej: "juan")
     * @return El userId asociado o null si no existe
     */
    public String getUserIdByUsername(String username) {
        return redisTemplate.opsForValue().get(USER_NAME_PREFIX + username);
    }

    /**
     * Obtiene el ID de la instancia donde está conectado un usuario.
     * Busca en Redis la clave user:{userId}:connectionInstance que se establece
     * cuando el usuario se conecta via WebSocket.
     * 
     * @param userId ID único del usuario (ej: "uuid-123")
     * @return El ID de la instancia donde está conectado (ej: "instance-1") o null si está offline
     */
    public String getConnectionInstance(String userId) {
        return redisTemplate.opsForValue().get(USER_INSTANCE_PREFIX + userId + USER_INSTANCE_SUFFIX);
    }

    /**
     * Verifica si un usuario está conectado (basado en Redis).
     * 
     * @param userId ID del usuario
     * @return true si el usuario está conectado en alguna instancia
     */
    public boolean isUserOnline(String userId) {
        String instance = getConnectionInstance(userId);
        return instance != null;
    }

    /**
     * Obtiene el ID de esta instancia.
     * 
     * @return El ID de la instancia
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Envía un mensaje a una sesión específica.
     * 
     * @param sessionId ID de la sesión destino
     * @param data Datos binarios del mensaje a enviar
     */
    public void sendToSession(String sessionId, byte[] data) {
        // Buscar la sesión por sessionId en el mapa local
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
                log.debug("Mensaje enviado exitosamente a sesión {}", sessionId);
            } catch (Exception e) {
                log.error("Error enviando mensaje a sesión {}: {}", sessionId, e.getMessage());
            }
        } else {
            log.warn("La sesión {} no existe o no está abierta en esta instancia", sessionId);
        }
    }

    /**
     * Envía un mensaje a un usuario específico.
     * 
     * Este método:
     * 1. Consulta Redis para verificar si el usuario está conectado
     * 2. Si está en esta instancia, envía directamente usando el mapa local
     * 3. Si está en otra instancia, delega el enrutamiento a MessageRouterService
     * 
     * @param userId ID del usuario destinatario
     * @param data Datos binarios del mensaje
     */
    public void sendToUser(String userId, byte[] data) {
        // Verificar en Redis si el usuario está conectado
        String instance = getConnectionInstance(userId);
        
        if (instance == null) {
            log.info("Usuario {} no está conectado (offline)", userId);
            return;
        }

        if (instance.equals(instanceId)) {
            // El usuario está en esta instancia - buscar en el mapa LOCAL
            SessionInfo sessionInfo = localSessions.get(userId);
            
            if (sessionInfo != null) {
                try {
                    if (sessionInfo.getSession().isOpen()) {
                        sessionInfo.getSession().sendMessage(new org.springframework.web.socket.BinaryMessage(data));
                        log.info("Mensaje enviado directamente a usuario {} (sessionId: {})", 
                                userId, sessionInfo.getSession().getId());
                    } else {
                        log.warn("La sesión del usuario {} no está abierta", userId);
                    }
                } catch (Exception e) {
                    log.error("Error enviando mensaje al usuario {}: {}", userId, e.getMessage());
                }
            } else {
                log.warn("Usuario {} registrado en Redis pero no encontrado en el mapa local de esta instancia", userId);
            }
        } else {
            // El usuario está en otra instancia - el MessageRouterService manejará el envío via RabbitMQ
            log.info("Usuario {} está en otra instancia {}. El mensaje será enrutado via RabbitMQ", 
                    userId, instance);
        }
    }

    /**
     * Envía un mensaje a un usuario específico usando su username.
     * 
     * @param username Nombre del usuario destinatario
     * @param data Datos binarios del mensaje
     */
    public void sendToUserByUsername(String username, byte[] data) {
        String userId = redisTemplate.opsForValue().get(USER_NAME_PREFIX + username);
        if (userId != null) {
            sendToUser(userId, data);
        } else {
            log.info("No se encontró userId para username: {}", username);
        }
    }

    /**
     * Clase que representa la información de una sesión de WebSocket.
     */
    public static class SessionInfo {
        private final String userId;
        private final String username;
        private final WebSocketSession session;

        public SessionInfo(String userId, String username, WebSocketSession session) {
            this.userId = userId;
            this.username = username;
            this.session = session;
        }

        public String getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public WebSocketSession getSession() {
            return session;
        }
    }
}
