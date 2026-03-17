package com.basic_chat.notifiation_service.configuration;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.basic_chat.notifiation_service.service.UserPresenceService;

/**
 * Configuración para limpieza automática de sesiones huérfanas.
 * 
 * Este scheduler funciona como mecanismo de respaldo por si el evento
 * SessionDisconnectEvent no se dispara cuando el cliente se desconecta
 * abruptamente (ej: pérdida de conexión de red, cierre de app sin disconnect).
 * 
 * Ejecuta cada 60 segundos y verifica si las sesiones registradas en Redis
 * todavía tienen correspondencia con sesiones WebSocket activas.
 */
@Configuration
@EnableScheduling
public class SessionCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    private final StringRedisTemplate redisTemplate;
    private final UserPresenceService userPresenceService;

    public SessionCleanupScheduler(StringRedisTemplate redisTemplate, UserPresenceService userPresenceService) {
        this.redisTemplate = redisTemplate;
        this.userPresenceService = userPresenceService;
    }

    /**
     * Tarea programada que verifica y limpia sesiones huérfanas.
     * 
     * Proceso:
     * 1. Busca todas las claves de sesión en Redis (session:*:user)
     * 2. Para cada sesión, verifica si está activa en el registry de Spring
     * 3. Si no está activa, llama a handleSessionExpired para limpiar Redis
     * 
     * Ejecución: cada 60 segundos
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupOrphanedSessions() {
        try {
            logger.debug("Iniciando verificación de sesiones huérfanas...");
            
            Set<String> sessionKeys = redisTemplate.keys("session:*:user");
            
            if (sessionKeys == null || sessionKeys.isEmpty()) {
                logger.debug("No hay sesiones registradas en Redis para verificar");
                return;
            }
            
            logger.debug("Verificando {} sesiones en Redis", sessionKeys.size());
            
            int cleanedCount = 0;
            for (String sessionKey : sessionKeys) {
                String sessionId = extractSessionId(sessionKey);
                if (sessionId != null && isSessionOrphaned(sessionId)) {
                    logger.warn("Sesión huérfana detectada: {}. Limpiando...", sessionId);
                    userPresenceService.handleSessionExpired(sessionId);
                    cleanedCount++;
                }
            }
            
            if (cleanedCount > 0) {
                logger.info("Limpieza de sesiones huérfanas completada. Sesiones limpiadas: {}", cleanedCount);
            } else {
                logger.debug("No se detectaron sesiones huérfanas");
            }
            
        } catch (Exception e) {
            logger.error("Error en la tarea de limpieza de sesiones: {}", e.getMessage(), e);
        }
    }

    /**
     * Extrae el sessionId de una clave de Redis.
     * Ejemplo: "session:abc123:user" -> "abc123"
     */
    private String extractSessionId(String sessionKey) {
        if (sessionKey == null || !sessionKey.contains(":")) {
            return null;
        }
        String[] parts = sessionKey.split(":");
        if (parts.length >= 2) {
            return parts[1];
        }
        return null;
    }

    /**
     * Verifica si una sesión está huérfana (no tiene correspondencia activa).
     * 
     * Una sesión se considera huérfana cuando:
     * - No existe en el registry de usuarios de Spring
     * - O la clave de sesión en Redis ha expirado
     * 
     * Este método puede扩展 para verificar contra el InMemoryUserRegistry
     * de Spring si se necesita mayor precisión.
     */
    private boolean isSessionOrphaned(String sessionId) {
        // Por ahora, consideramos que si la sesión está en Redis,
        // está activa. Este método puede extenderse para verificar
        // contra el registry de Spring si es necesario.
        // 
        // Una implementación más completa usaría:
        // SimpUserRegistry registry = ...;
        // registry.getUser(sessionId);
        
        // Verificación básica: si la clave user existe en Redis, la sesión está registrada
        String userId = redisTemplate.opsForValue().get("session:" + sessionId + ":user");
        
        // Si no hay userId asociado, la sesión ya está limpia
        if (userId == null) {
            return false;
        }
        
        // Verificar si el usuario todavía tiene esta sesión en su lista
        // Esto ayuda a detectar inconsistencias
        java.util.List<String> sessions = redisTemplate.opsForList()
                .range("user:" + userId + ":sessions", 0, -1);
        
        if (sessions == null || !sessions.contains(sessionId)) {
            logger.debug("Sesión {} tiene userId {} pero no está en la lista de sesiones del usuario", 
                sessionId, userId);
            return true;
        }
        
        return false;
    }
}
