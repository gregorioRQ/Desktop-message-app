package com.pola.media_service.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * Servicio para consultar sesiones WebSocket almacenadas en Redis.
 * 
 * Redis es la fuente de verdad para saber si un usuario está online.
 * El API Gateway es responsable de mantener y limpiar estas claves.
 * 
 * Formato de claves Redis:
 * - ws:sessionid:{userId} → sessionId (mapeo usuario a su sesión WebSocket)
 * - ws:session:{userId} → "active" (indicador de sesión activa)
 */
@Service
@Slf4j
public class RedisSessionService {
    
    private final RedisTemplate<String, String> redisTemplate;
    
    private static final String SESSION_ID_KEY_PREFIX = "ws:sessionid:";
    private static final String SESSION_STATUS_KEY_PREFIX = "ws:session:";

    public RedisSessionService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Obtiene el sessionId de un usuario desde Redis.
     * 
     * @param userId ID del usuario
     * @return sessionId si existe, null en caso contrario
     */
    public String getSessionId(String userId) {
        String key = SESSION_ID_KEY_PREFIX + userId;
        String sessionId = redisTemplate.opsForValue().get(key);
        log.debug("Obteniendo sessionId para userId: {} - Resultado: {}", userId, sessionId != null ? "encontrado" : "no encontrado");
        return sessionId;
    }

    /**
     * Verifica si un usuario está online consultando Redis.
     * 
     * @param userId ID del usuario
     * @return true si el usuario tiene una sesión activa en Redis
     */
    public boolean isUserOnline(String userId) {
        String key = SESSION_STATUS_KEY_PREFIX + userId;
        Boolean exists = redisTemplate.hasKey(key);
        log.debug("Verificando si {} está online en Redis: {}", userId, exists);
        return exists != null && exists;
    }

    /**
     * Obtiene el sessionId de un usuario por su username.
     * 
     * IMPORTANTE: Esta es una operación que requiere un mapeo username -> userId en Redis.
     * El API Gateway es responsable de mantener este mapeo.
     * 
     * Formato: ws:username:{username} → {userId}
     * 
     * @param username Username del usuario
     * @return sessionId si existe, null en caso contrario
     */
    public String getSessionIdByUsername(String username) {
        String userIdKey = "ws:username:" + username;
        String userId =  redisTemplate.opsForValue().get(userIdKey);
        
        if (userId == null) {
            log.debug("No se encontró userId para username: {}", username);
            return null;
        }
        
        return getSessionId(userId);
    }

    /**
     * Verifica si un usuario está online por su username.
     * 
     * @param username Username del usuario
     * @return true si el usuario tiene una sesión activa en Redis
     */
    public boolean isUserOnlineByUsername(String username) {
        String userIdKey = "ws:username:" + username;
        String userId = redisTemplate.opsForValue().get(userIdKey);
        
        if (userId == null) {
            log.debug("No se encontró userId para username: {}", username);
            return false;
        }
        
        return isUserOnline(userId);
    }

    /**
     * Guarda el sessionId de un usuario en Redis.
     * Normalmente lo hace el API Gateway, pero este método está disponible para consistencia.
     * 
     * @param userId ID del usuario
     * @param sessionId ID de la sesión WebSocket
     */
    public void saveSessionId(String userId, String sessionId) {
        String key = SESSION_ID_KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, sessionId);
        log.debug("SessionId guardado en Redis - userId: {}, sessionId: {}", userId, sessionId);
    }

    /**
     * Elimina el sessionId de un usuario de Redis.
     * 
     * @param userId ID del usuario
     */
    public void removeSessionId(String userId) {
        String key = SESSION_ID_KEY_PREFIX + userId;
        redisTemplate.delete(key);
        log.debug("SessionId removido de Redis - userId: {}", userId);
    }

    /**
     * Verifica si existe un mapeo de sesión para un usuario en Redis.
     * Esto es usado para validar que el API Gateway ha preparado la sesión.
     *
     * @param userId ID del usuario
     * @return true si el mapeo de sesión existe
     */
    public boolean hasSessionMapping(String userId) {
        String key = SESSION_ID_KEY_PREFIX + userId;
        Boolean exists = redisTemplate.hasKey(key);
        log.debug("Verificando si existe mapeo de sesión para userId: {} - Resultado: {}", userId, exists != null && exists);
        return exists != null && exists;
    }

}

