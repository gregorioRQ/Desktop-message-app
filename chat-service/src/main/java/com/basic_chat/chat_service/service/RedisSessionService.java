package com.basic_chat.chat_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Servicio para consultar sesiones WebSocket almacenadas en Redis.
 * 
 * Redis es la fuente de verdad para saber si un usuario está online.
 * El notification-service es responsable de mantener y limpiar estas claves.
 * 
 * Formato de claves Redis:
 * - user:{userId}:sessions → lista de sessionIds activas para el usuario
 * - session:{sessionId}:user → userId del propietario de la sesión
 * - user:name:{username} → userId (mapeo para verificar online por username)
 */
@Service
public class RedisSessionService {
    
    private static final Logger log = LoggerFactory.getLogger(RedisSessionService.class);
    
    private final StringRedisTemplate redisTemplate;
    
    private static final String USER_SESSIONS_KEY_PREFIX = "user:";
    private static final String USER_SESSIONS_KEY_SUFFIX = ":sessions";
    private static final String SESSION_USER_KEY_PREFIX = "session:";
    private static final String SESSION_USER_KEY_SUFFIX = ":user";

    public RedisSessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Obtiene una sessionId de un usuario desde Redis.
     * Si el usuario tiene múltiples sesiones activas, se devuelve una cualquiera.
     * 
     * @param userId ID del usuario
     * @return sessionId si existe, null en caso contrario
     */
    public String getSessionId(String userId) {
        String key = USER_SESSIONS_KEY_PREFIX + userId + USER_SESSIONS_KEY_SUFFIX;
        // Obtiene un miembro arbitrario de la lista de sesiones
        List<String> sessions = redisTemplate.opsForList().range(key, 0, -1);
        if (sessions != null && !sessions.isEmpty()) {
            String sessionId = sessions.get(0);
            log.debug("Obteniendo sessionId para userId: {} - Resultado: {}", userId, sessionId != null ? "encontrado" : "no encontrado");
            return sessionId;
        }
        log.debug("Obteniendo sessionId para userId: {} - Resultado: no encontrado", userId);
        return null;
    }

    /**
     * Verifica si un usuario está online consultando Redis.
     * Un usuario está online si tiene al menos una sesión activa en su lista.
     * 
     * @param userId ID del usuario
     * @return true si el usuario tiene una sesión activa en Redis
     */
    public boolean isUserOnline(String userId) {
        String key = USER_SESSIONS_KEY_PREFIX + userId + USER_SESSIONS_KEY_SUFFIX;
        Long size = redisTemplate.opsForList().size(key);
        boolean isOnline = size != null && size > 0;
        log.debug("Verificando si {} está online en Redis: {}", userId, isOnline);
        return isOnline;
    }

    /**
     * Obtiene el sessionId de un usuario a través del mapeo username -> userId.
     * 
     * Este método primero busca el userId en Redis usando la clave user:name:{username}
     * y luego obtiene una sesión activa de ese usuario.
     * 
     * El mapeo username -> userId es creado por el notification-service cuando
     * el usuario se conecta via WebSocket STOMP.
     * 
     * @param username Username del usuario
     * @return sessionId si existe, null en caso contrario
     */
    public String getSessionIdByUsername(String username) {
        String userIdKey = "user:name:" + username;
        String userId = redisTemplate.opsForValue().get(userIdKey);
        
        if (userId == null) {
            log.debug("No se encontró userId para username: {}", username);
            return null;
        }
        
        return getSessionId(userId);
    }

    /**
     * Verifica si un usuario está online usando su username.
     * 
     * Este método primero busca el userId en Redis usando la clave user:name:{username}
     * y luego verifica si ese usuario tiene sesiones activas.
     * 
     * El mapeo username -> userId es creado por el notification-service cuando
     * el usuario se conecta via WebSocket STOMP.
     * 
     * @param username Username del usuario
     * @return true si el usuario tiene una sesión activa en Redis
     */
    public boolean isUserOnlineByUsername(String username) {
        String userIdKey = "user:name:" + username;
        String userId = redisTemplate.opsForValue().get(userIdKey);
        if (userId == null) {
            log.warn("No se encontró userId para username: {}", username);
            return false;
        }else{
            return true;
        }
        
        //return isUserOnline(userId);
    }

    /**
     * Agrega una sessionId al conjunto de sesiones de un usuario en Redis.
     * También crea la relación inversa de sesión a usuario.
     * 
     * @param userId ID del usuario
     * @param sessionId ID de la sesión WebSocket
     */
    public void addSessionId(String userId, String sessionId) {
        // Agregar sessionId a la lista del usuario
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId + USER_SESSIONS_KEY_SUFFIX;
        redisTemplate.opsForList().rightPush(userSessionsKey, sessionId);
        
        // Crear la relación inversa de sesión a usuario
        String sessionUserKey = SESSION_USER_KEY_PREFIX + sessionId + SESSION_USER_KEY_SUFFIX;
        redisTemplate.opsForValue().set(sessionUserKey, userId);
        
        log.debug("SessionId agregada en Redis - userId: {}, sessionId: {}", userId, sessionId);
    }

    /**
     * Elimina todas las sessionIds de un usuario de Redis.
     * 
     * @param userId ID del usuario
     */
    public void removeAllSessionsForUser(String userId) {
        String userSessionsKey = USER_SESSIONS_KEY_PREFIX + userId + USER_SESSIONS_KEY_SUFFIX;
        // Primero obtenemos todas las sesiones para eliminar las referencias inversas
        List<String> sessionIds = redisTemplate.opsForList().range(userSessionsKey, 0, -1);
        if (sessionIds != null) {
            for (String sessionId : sessionIds) {
                String sessionUserKey = SESSION_USER_KEY_PREFIX + sessionId + SESSION_USER_KEY_SUFFIX;
                redisTemplate.delete(sessionUserKey);
            }
        }
        // Luego eliminamos la lista del usuario
        redisTemplate.delete(userSessionsKey);
        log.debug("Todas las sessionIds removidas de Redis para userId: {}", userId);
    }
}
