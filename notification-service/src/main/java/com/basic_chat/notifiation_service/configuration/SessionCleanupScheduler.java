package com.basic_chat.notifiation_service.configuration;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuración para limpieza automática de sesiones huérfanas (LEGACY - COMENTADO).
 * 
 * Este scheduler verificaba y limpiaba sesiones huérfanas en Redis.
 * Fue comentaddo porque interfería con las sesiones de connection-service.
 * 
 * Mantenido por si se necesita en el futuro.
 * 
 * @deprecated Ya no se utiliza. Las sesiones ahora se gestionan vía SSE.
 */
// @Configuration
// @EnableScheduling
public class SessionCleanupScheduler {

    private static final Logger logger = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    /**
     * Constructor vacío para evitar errores de compilación.
     * El scheduler ya no se usa.
     */
    public SessionCleanupScheduler() {
        logger.info("SessionCleanupScheduler comentarios - no se ejecutará");
    }

    // private final StringRedisTemplate redisTemplate;
    // private final UserPresenceService userPresenceService;

    // public SessionCleanupScheduler(StringRedisTemplate redisTemplate, UserPresenceService userPresenceService) {
    //     this.redisTemplate = redisTemplate;
    //     this.userPresenceService = userPresenceService;
    // }

    /**
     * Tarea programada que verifica y limpia sesiones huérfanas.
     * 
     * Proceso:
     * 1. Busca todas las claves de sesión en Redis (session:*:user)
     * 2. Para cada sesión, verifica si está activa en el registry de Spring
     * 3. Si no está activa, llama a handleSessionExpired para limpiar Redis
     * 
     * Ejecución: cada 60 segundos
     * 
     * @deprecated Reemplazado por gestión de sesiones SSE
     */
    // @Scheduled(fixedRate = 60000)
    // public void cleanupOrphanedSessions() {
    //     try {
    //         logger.debug("Iniciando verificación de sesiones huérfanas...");
    //         
    //         Set<String> sessionKeys = redisTemplate.keys("session:*:user");
    //         
    //         if (sessionKeys == null || sessionKeys.isEmpty()) {
    //             logger.debug("No hay sesiones registradas en Redis para verificar");
    //             return;
    //         }
    //         
    //         logger.debug("Verificando {} sesiones en Redis", sessionKeys.size());
    //         
    //         int cleanedCount = 0;
    //         for (String sessionKey : sessionKeys) {
    //             String sessionId = extractSessionId(sessionKey);
    //             if (sessionId != null && isSessionOrphaned(sessionId)) {
    //                 logger.warn("Sesión huérfana detectada: {}. Limpiando...", sessionId);
    //                 userPresenceService.handleSessionExpired(sessionId);
    //                 cleanedCount++;
    //             }
    //         }
    //         
    //         if (cleanedCount > 0) {
    //             logger.info("Limpieza de sesiones huérfanas completada. Sesiones limpiadas: {}", cleanedCount);
    //         } else {
    //             logger.debug("No se detectaron sesiones huérfanas");
    //         }
    //         
    //     } catch (Exception e) {
    //         logger.error("Error en la tarea de limpieza de sesiones: {}", e.getMessage(), e);
    //     }
    // }

    /**
     * Extrae el sessionId de una clave de Redis.
     * Ejemplo: "session:abc123:user" -> "abc123"
     * 
     * @deprecated Método no utilizado
     */
    // private String extractSessionId(String sessionKey) {
    //     if (sessionKey == null || !sessionKey.contains(":")) {
    //         return null;
    //     }
    //     String[] parts = sessionKey.split(":");
    //     if (parts.length >= 2) {
    //         return parts[1];
    //     }
    //     return null;
    // }

    /**
     * Verifica si una sesión está huérfana (no tiene correspondencia activa).
     * 
     * @deprecated Método no utilizado
     */
    // private boolean isSessionOrphaned(String sessionId) {
    //     String userId = redisTemplate.opsForValue().get("session:" + sessionId + ":user");
    //     if (userId == null) {
    //         return false;
    //     }
    //     java.util.List<String> sessions = redisTemplate.opsForList()
    //             .range("user:" + userId + ":sessions", 0, -1);
    //     if (sessions == null || !sessions.contains(sessionId)) {
    //         return true;
    //     }
    //     return false;
    // }
}
