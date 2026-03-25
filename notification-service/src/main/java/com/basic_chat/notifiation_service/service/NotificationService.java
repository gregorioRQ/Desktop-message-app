package com.basic_chat.notifiation_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servicio de notificaciones (LEGACY - COMENTADO).
 * 
 * Este servicio manejaba el envío de notificaciones vía STOMP WebSocket y
 * la gestión de contactos. Fue comentado porque:
 * 1. Las notificaciones ahora se envían vía SSE (SseNotificationService)
 * 2. La gestión de presencia fue comentada (UserPresenceService)
 * 3. Interfería con las sesiones de connection-service
 * 
 * Mantenido por si se necesita en el futuro para referencia o reutilización.
 * 
 * @deprecated Reemplazado por SseNotificationService para notificaciones.
 */
// @Service
// @Transactional
public class NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    /**
     * Constructor vacío para evitar errores de compilación.
     * El servicio ya no se usa activamente.
     */
    public NotificationService() {
        logger.info("NotificationService comentarios - no se ejecutará");
    }

    // private final NotificationRepository notificationRepository;
    // private final SimpMessagingTemplate messagingTemplate;
    // private final UserContactRepository userContactRepository;
    // private final UserRepository userRepository;
    // private final StringRedisTemplate redisTemplate;
    // private final UserPresenceService userPresenceService;

    // public NotificationService(NotificationRepository notificationRepository, SimpMessagingTemplate messagingTemplate, 
    //                            UserContactRepository userContactRepository, UserRepository userRepository,
    //                            StringRedisTemplate redisTemplate, UserPresenceService userPresenceService) {
    //     this.notificationRepository = notificationRepository;
    //     this.messagingTemplate = messagingTemplate;
    //     this.userContactRepository = userContactRepository;
    //     this.userRepository = userRepository;
    //     this.redisTemplate = redisTemplate;
    //     this.userPresenceService = userPresenceService;
    // }

    // public void createNotification(Notification notification) {
    //     notificationRepository.save(notification);
    // }

    // public void deleteNotification(Long id) {
    //     notificationRepository.deleteById(id);
    // }

    // public void deleteAllNotificationsByUserId(String userId) {
    //     notificationRepository.deleteAllByReceiver(userId);
    // }

    // public List<Notification> getUnseenNotificationsByUserId(String userId) {
    //     return notificationRepository.findByReceiverAndIsSeenFalse(userId);
    // }

    // /**
    //  * Notifica a un usuario específico a través de WebSocket.
    //  * @deprecated Metodo legacy - reemplazado por SseNotificationService
    //  */
    // public void notifyUser(Notification event) {
    //     String destination = "/topic/notifications/" + event.getReceiver();
    //     messagingTemplate.convertAndSend(destination, event);
    // }

    // /**
    //  * Notifica a un usuario que su mensaje ha sido visto.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // public void notifyMessageSeen(Notification event) {
    //     String destination = "/topic/seen/" + event.getReceiver();
    //     messagingTemplate.convertAndSend(destination, event);
    // }

    // /**
    //  * Agrega un nuevo contacto para un usuario.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // public void addContact(String fromId, String contactId) { ... }

    // /**
    //  * Elimina contactos para un usuario.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // public void removeContacts(String userId, List<String> contactIds) { ... }
}
