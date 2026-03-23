package com.basic_chat.notifiation_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servicio responsable de gestionar la presencia de usuarios (LEGACY - COMENTADO).
 * 
 * Este servicio manejaba la lógica de conexión/desconexión de sesiones WebSocket
 * y notificaciones de presencia online/offline. Fue comentado porque:
 * 1. Interfería con las sesiones de connection-service en Redis
 * 2. La funcionalidad de presencia se implementará después con otro enfoque
 * 
 * Mantenido por si se necesita en el futuro para referencia o reutilización.
 * 
 * @deprecated Reemplazado por SSE para notificaciones y lógica de presencia por implementar.
 */
// @Service
// @Transactional
public class UserPresenceService {
    
    private static final Logger logger = LoggerFactory.getLogger(UserPresenceService.class);

    /**
     * Constructor vacío para evitar errores de compilación.
     * El servicio ya no se usa activamente.
     */
    public UserPresenceService() {
        logger.info("UserPresenceService comentarios - no se ejecutará");
    }

    // private final StringRedisTemplate redisTemplate;
    // private final SimpMessagingTemplate messagingTemplate;
    // private final UserRepository userRepository;
    // private final UserContactRepository userContactRepository;

    // public UserPresenceService(StringRedisTemplate redisTemplate, SimpMessagingTemplate messagingTemplate,
    //                            UserRepository userRepository, UserContactRepository userContactRepository) {
    //     this.redisTemplate = redisTemplate;
    //     this.messagingTemplate = messagingTemplate;
    //     this.userRepository = userRepository;
    //     this.userContactRepository = userContactRepository;
    // }

    // /**
    //  * Constantes para claves de Redis.
    //  */
    // private static final String USER_SESSIONS_PREFIX = "user:";
    // private static final String USER_SESSIONS_SUFFIX = ":sessions";
    // private static final String SESSION_USER_PREFIX = "session:";
    // private static final String SESSION_USER_SUFFIX = ":user";
    // private static final String USER_NAME_PREFIX = "user:name:";

    // /**
    //  * Registra una nueva sesión de usuario en Redis.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // public void handleSessionConnected(String userId, String username, String sessionId) { ... }

    // /**
    //  * Maneja la desconexión de una sesión WebSocket.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // public void handleSessionDisconnect(String sessionId) { ... }

    // /**
    //  * Maneja la expiración de una sesión por falla de heartbeat.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // public void handleSessionExpired(String sessionId) { ... }

    // /**
    //  * Notifica a suscriptores que un usuario está online.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // public void notifyUserOnline(String userId) { ... }

    // /**
    //  * Notifica a suscriptores que un usuario está offline.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // public void notifyUserOffline(String userId) { ... }

    // /**
    //  * Suscribe una sesión a cambios de presencia de un contacto.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // public void subscribeToContact(String sessionId, String contactId) { ... }

    // /**
    //  * Elimina suscripciones bidireccionales.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // public void unsubscribeBidirectional(String userId, String contactId) { ... }
}
