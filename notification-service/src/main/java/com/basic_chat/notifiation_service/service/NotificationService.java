package com.basic_chat.notifiation_service.service;

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
     * Notifica a un usuario que su ah sido visto.
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
            logger.info("Iniciando proceso de adición de contacto. Usuario origen: {}, Nuevo contacto: {}", 
                fromId, contactId);
            
            // Validar que el usuario origen existe en el sistema
            User user = userRepository.findById(fromId)
                    .orElseThrow(() -> new EntityNotFoundException(
                        "El usuario: " + fromId + " no se halla en el sistema"));
            logger.info("Usuario origen validado correctamente: {}", fromId);
            
            // Buscar al usuario que será agregado como contacto
            Optional<User> contactOpt = userRepository.findById(contactId);
            
            if (contactOpt.isPresent()) {
                User contactUser = contactOpt.get();
                logger.info("Usuario contacto encontrado: {}. Iniciando proceso de vinculación.", contactId);
                
                // 1. Verificar y crear relación fromId -> contactId
                boolean contactExists = userContactRepository.findByUser(user).stream()
                        .anyMatch(c -> c.getContact().getId().equals(contactId));

                if (!contactExists) {
                    // Crear relación unidireccional: fromId -> contactId
                    UserContact userContact = new UserContact();
                    userContact.setUser(user);
                    userContact.setContact(contactUser);
                    userContactRepository.save(userContact);
                    logger.debug("Relación guardada: {} ahora tiene a {} como contacto", fromId, contactId);
                    
                    // Obtener sesiones activas del usuario origen y suscribirlas al nuevo contacto
                    List<String> sessions = redisTemplate.opsForList()
                            .range("user:" + fromId + ":sessions", 0, -1);
                    if (sessions != null && !sessions.isEmpty()) {
                        logger.debug("Usuario {} tiene {} sesiones activas. Suscribiendo al contacto {}", 
                            fromId, sessions.size(), contactId);
                        for (String sessionId : sessions) {
                            userPresenceService.subscribeToContact(sessionId, contactUser.getId());
                        }
                    } else {
                        logger.debug("Usuario {} no tiene sesiones activas en este momento", fromId);
                    }
                } else {
                    logger.info("El contacto {} ya existe para el usuario {}. Se omite la creación.", contactId, fromId);
                }

                // 2. Verifica si el usuario que recibio la solicitud tiene como contacto al usuario que le envio la solicitud y crea la relación inversa contactId -> fromId
                boolean reverseExists = userContactRepository.findByUser(contactUser).stream()
                        .anyMatch(c -> c.getContact().getId().equals(fromId));

                if (!reverseExists) {
                    // Crear relación bidireccional: contactId -> fromId (relación inversa)
                    UserContact reverseContact = new UserContact();
                    reverseContact.setUser(contactUser);
                    reverseContact.setContact(user);
                    userContactRepository.save(reverseContact);
                    logger.debug("Relación inversa guardada: {} ahora tiene a {} como contacto", contactId, fromId);

                    // Obtener sesiones activas del usuario contacto y suscribirlas al usuario origen
                    List<String> contactSessions = redisTemplate.opsForList()
                            .range("user:" + contactId + ":sessions", 0, -1);
                    if (contactSessions != null && !contactSessions.isEmpty()) {
                        logger.debug("Usuario {} tiene {} sesiones activas. Suscribiendo al usuario {}", 
                            contactId, contactSessions.size(), fromId);
                        for (String sessionId : contactSessions) {
                            userPresenceService.subscribeToContact(sessionId, user.getId());
                        }
                    } else {
                        logger.debug("Usuario {} no tiene sesiones activas en este momento", contactId);
                    }
                } else {
                    logger.info("El contacto inverso {} ya existe para el usuario {}. Se omite la creación.", fromId, contactId);
                }
                
                logger.info("Proceso de adición de contacto completado exitosamente. {} y {} ahora son contactos mutuos", 
                    fromId, contactId);
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
}
