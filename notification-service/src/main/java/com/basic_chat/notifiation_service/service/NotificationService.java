
/**
 * NotificationService gestiona la lógica central de notificaciones en la aplicación de mensajería.
 * Se encarga de crear, eliminar y distribuir notificaciones a los usuarios a través de WebSocket,
 * así como de administrar relaciones de contactos y suscripciones de presencia. Orquesta la integración
 * entre la base de datos, Redis y la mensajería en tiempo real, asegurando la coherencia y entrega
 * eficiente de eventos relevantes para los usuarios.
 */
package com.basic_chat.notifiation_service.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.notifiation_service.model.Notification;
import com.basic_chat.notifiation_service.repository.NotificationRepository;
import com.basic_chat.notifiation_service.model.User;
import com.basic_chat.notifiation_service.model.UserContact;
import com.basic_chat.notifiation_service.repository.UserContactRepository;
import com.basic_chat.notifiation_service.repository.UserRepository;

import jakarta.persistence.EntityNotFoundException;

@Service
@Transactional
public class NotificationService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserContactRepository userContactRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final UserPresenceService userPresenceService;

    public NotificationService(NotificationRepository notificationRepository, SimpMessagingTemplate messagingTemplate, 
                               UserContactRepository userContactRepository, UserRepository userRepository,
                               StringRedisTemplate redisTemplate, UserPresenceService userPresenceService) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.userContactRepository = userContactRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.userPresenceService = userPresenceService;
    }

    public void createNotification(Notification notification) {

        notificationRepository.save(notification);
    }

    public void deleteNotification(Long id) {
        notificationRepository.deleteById(id);
    }

    public void deleteAllNotificationsByUserId(String userId) {
        notificationRepository.deleteAllByReceiver(userId);
    }

    public List<Notification> getUnseenNotificationsByUserId(String userId) {
        return notificationRepository.findByReceiverAndIsSeenFalse(userId);

    }

    /**
     * Notifica a un usuario específico a través de WebSocket.
     * 
     * @param userId
     * @param message
     */
    public void notifyUser(Notification event) {
        // Enviar al destino STOMP específico de ese usuario
        String destination = "/topic/notifications/" + event.getReceiver();
        messagingTemplate.convertAndSend(destination, event);
    }

    /**
     * Notifica a un usuario que su mensaje ha sido visto.
     * @param event
     */
    public void notifyMessageSeen(Notification event) {
        // Enviar al destino STOMP específico de ese usuario
        String destination = "/topic/seen/" + event.getReceiver();
        messagingTemplate.convertAndSend(destination, event);
    }

    /**
     * Agrega un nuevo contacto para un usuario y configura las suscripciones de WebSocket.
     * Este método maneja la relación bidireccional entre usuarios y suscribe las sesiones
     * activas a los cambios de presencia del otro usuario.
     * 
     * @param fromId ID del usuario que agrega el contacto
     * @param contactId ID del usuario que será agregado como contacto
     * @throws EntityNotFoundException si alguno de los usuarios no existe
     * @throws RuntimeException si ocurre un error inesperado durante el proceso
     */
    public void addContact(String fromId, String contactId) {
        try {
            if (fromId != null && fromId.equals(contactId)) {
                logger.warn("Operación cancelada: El usuario {} intentó agregarse a sí mismo.", fromId);
                return;
            }

            logger.info("Iniciando proceso de adición de contacto. Usuario origen: {}, Nuevo contacto: {}", 
                fromId, contactId);
            
            User user = userRepository.findById(fromId)
                    .orElseThrow(() -> new EntityNotFoundException(
                        "El usuario: " + fromId + " no sehalla en el sistema"));
            logger.info("Usuario origen validado correctamente: {}", fromId);
            
            Optional<User> contactOpt = userRepository.findById(contactId);
            
            if (contactOpt.isPresent()) {
                try{
                    User contactUser = contactOpt.get();
                logger.info("Usuario contacto encontrado: {}. Iniciando proceso de vinculación.", contactId);
                
                saveContactRelation(user, contactUser);
                saveContactRelation(contactUser, user);
                
                subscribeUserSessionsToContactAsync(fromId, contactId);
                subscribeUserSessionsToContactAsync(contactId, fromId);
                
                logger.info("Proceso de adición de contacto completado exitosamente. {} y {} ahora son contactos mutuos", 
                    fromId, contactId);
                }catch(Exception ex){
                    logger.error("No se puedo procesar la creacion de la relacion. Error: {}", ex);
                    throw new RuntimeException(ex);
                }
                
            } else {
                logger.warn("Intento de agregar contacto inexistente. Usuario origen: {}, Contacto inexistente: {}", 
                    fromId, contactId);
                return;
            }
        } catch (Exception e) {
            logger.error("Error inesperado al agregar contacto. De: {}, Contacto: {}. Excepción: {}", 
                fromId, contactId, e.getMessage(), e);
            throw new RuntimeException(
                "Error al procesar la adición de contacto entre " + fromId + " y " + contactId + ": " + e.getMessage(), e);
        }
    }

    @Transactional
    private void saveContactRelation(User source, User target) {
        if(source == null || target == null){
            logger.warn("Parametros invalidos source: {}, target: {} operacion cancelada.", source, target);
            return;
        }
        boolean exists = userContactRepository.findByUser(source).stream()
            .anyMatch(c -> c.getContact().getId().equals(target.getId()));

        if (!exists) {
            UserContact userContact = new UserContact(source, target);
            userContactRepository.save(userContact);
            logger.debug("Relación guardada: {} ahora tiene a {} como contacto", source.getId(), target.getId());
        } else {
            logger.info("El contacto {} ya existe para el usuario {}. Se omite la creación.", target.getId(), source.getId());
        }
    }

    private void subscribeUserSessionsToContactAsync(String userId, String contactId) {
        try {
            subscribeUserSessionsToContact(userId, contactId);
        } catch (Exception e) {
            logger.error("Error al suscribir sesiones a contacto (no crítico). userId: {}, contactId: {}", 
                userId, contactId, e);
        }
    }

    private void subscribeUserSessionsToContact(String userId, String contactId) {
        
         if(userId == null || contactId == null){
                logger.warn("Suscripcion a redis cancelada parametros invalidos. userId: {}, contactId: {}", userId, contactId);
                return;
            }
        
        try{
           
            List<String> sessions = redisTemplate.opsForList()
                .range("user:" + userId + ":sessions", 0, -1);
        if (sessions != null && !sessions.isEmpty()) {
            logger.debug("Usuario {} tiene {} sesiones activas. Suscribiendo al contacto {}", 
                userId, sessions.size(), contactId);
            for (String sessionId : sessions) {
                userPresenceService.subscribeToContact(sessionId, contactId);
            }
        } else {
            logger.debug("Usuario {} no tiene sesiones activas en este momento", userId);
        }
        }catch(Exception ex){
            logger.error("Error al suscribir sesiones a contacto (no crítico). userId: {}, contactId: {}", userId, contactId, ex);
        }
        
    }

    /**
     * Elimina una lista de contactos para un usuario específico.
     * Realiza la eliminación en la base de datos y limpia las suscripciones en Redis.
     * 
     * @param userId ID del usuario que solicita la eliminación
     * @param contactIds Lista de IDs de contactos a eliminar
     */
    public void removeContacts(String userId, List<String> contactIds) {
        List<String> failed = new ArrayList<>();
    int successCount = 0;

    try {
        if (contactIds == null || contactIds.isEmpty()) {
            sendContactDropResponse(userId, false, "Lista vacía");
            return;
        }

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado: " + userId));

        for (String contactId : contactIds) {
            if (processContactRemoval(user, contactId)) {
                successCount++;
            } else {
                failed.add(contactId);
            }
        }
        
        String message = String.format("Eliminados %d de %d contactos", 
            successCount, contactIds.size());
        sendContactDropResponse(userId, failed.isEmpty(), message);
        
    } catch (EntityNotFoundException e) {
        logger.warn("Usuario no encontrado: {}", userId);
        sendContactDropResponse(userId, false, "Usuario no encontrado");
    } catch (Exception e) {
        logger.error("Error crítico: {}", e.getMessage(), e);
        sendContactDropResponse(userId, false, "Error interno");
    }
}

    /**
     * Orquesta la eliminación de un contacto individual: relación directa, inversa y suscripciones.
     * Captura excepciones para no interrumpir el procesamiento de la lista principal.
     * 
     * @param user Usuario que solicita la eliminación
     * @param contactId ID del contacto a eliminar
     */
   private boolean processContactRemoval(User user, String contactId) {
    try {
        deleteDirectContact(user, contactId);
        deleteInverseContact(user.getId(), contactId);
        
        try {
            userPresenceService.unsubscribeBidirectional(user.getId(), contactId);
        } catch (Exception e) {
            logger.error("Error Redis: {}", e.getMessage());
            // Continuar - Redis no es crítico
        }
        return true;
    } catch (Exception e) {
        logger.error("Error al eliminar contacto {}: {}", contactId, e.getMessage());
        return false;
    }
}

    /**
     * Elimina la relación directa del usuario hacia el contacto en la base de datos.
     */
    private void deleteDirectContact(User user, String contactId) {
        Optional<UserContact> userToContact = userContactRepository.findByUser(user).stream()
                .filter(c -> c.getContact().getId().equals(contactId))
                .findFirst();
        
        userToContact.ifPresent(uc -> {
            userContactRepository.delete(uc);
            logger.debug("Relación eliminada: {} -> {}", user.getId(), contactId);
        });
    }

    /**
     * Elimina la relación inversa del contacto hacia el usuario en la base de datos.
     */
    private void deleteInverseContact(String userId, String contactId) {
        Optional<User> contactUserOpt = userRepository.findById(contactId);
        if (contactUserOpt.isPresent()) {
            Optional<UserContact> contactToUser = userContactRepository.findByUser(contactUserOpt.get()).stream()
                    .filter(c -> c.getContact().getId().equals(userId))
                    .findFirst();
            
            contactToUser.ifPresent(uc -> {
                userContactRepository.delete(uc);
                logger.debug("Relación inversa eliminada: {} -> {}", contactId, userId);
            });
        }
    }

    private void sendContactDropResponse(String userId, boolean success, String message) {
        try {
            Map<String, Object> response = Map.of(
                "type", "contact_dropped",
                "success", success,
                "message", message
            );
            messagingTemplate.convertAndSend("/topic/notifications/" + userId, response);
        } catch (Exception ex) {
            logger.error("Error al enviar respuesta WebSocket a {}", userId, ex);
        }
    }
}
