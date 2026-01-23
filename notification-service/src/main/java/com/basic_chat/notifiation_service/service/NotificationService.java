package com.basic_chat.notifiation_service.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserContactRepository userContactRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    public NotificationService(NotificationRepository notificationRepository, SimpMessagingTemplate messagingTemplate, 
                               UserContactRepository userContactRepository, UserRepository userRepository,
                               StringRedisTemplate redisTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.userContactRepository = userContactRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
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
        System.out.println(event.getReceiver());
        messagingTemplate.convertAndSend(destination, event);
    }

    public void notifyMessageSeen(Notification event) {
        // Enviar al destino STOMP específico de ese usuario
        String destination = "/topic/seen/" + event.getReceiver();
        messagingTemplate.convertAndSend(destination, event);
    }

    public void addContact(String fromId, String contactId) {
        User user = userRepository.findById(fromId)
                .orElseThrow(() -> new EntityNotFoundException("El usuario: " + fromId + " no se halla en el sistema"));
                
        Optional<User> contactOpt = userRepository.findById(contactId);
        
        if (contactOpt.isPresent()) {
            User contactUser = contactOpt.get();
            
            UserContact userContact = new UserContact();
            userContact.setUser(user);
            userContact.setContact(contactUser);
            userContactRepository.save(userContact);
            
            // Fase 4: Si Juan está conectado, suscribir a Luis
            List<String> sessions = redisTemplate.opsForList().range("user:" + fromId + ":sessions", 0, -1);
            if (sessions != null) {
                for (String sessionId : sessions) {
                    subscribeToContact(sessionId, contactUser.getId());
                }
            }

            // Actualizar la lista de contactos de uB (contactUser) agregando a uA (user)
            UserContact reverseContact = new UserContact();
            reverseContact.setUser(contactUser);
            reverseContact.setContact(user);
            userContactRepository.save(reverseContact);

            // Si uB tiene sesiones activas, suscribirlas a uA
            List<String> contactSessions = redisTemplate.opsForList().range("user:" + contactId + ":sessions", 0, -1);
            if (contactSessions != null) {
                for (String sessionId : contactSessions) {
                    subscribeToContact(sessionId, user.getId());
                }
            }
        } else {
            System.out.println("No se encontró usuario para el contacto: " + contactId);
        }
    }

    public void handleSessionConnected(String userId, String sessionId) {
        // Fase 1: Registrar sesión y suscripciones
       
        System.out.println("VERIFICANDO SI EL USUARIO TIENE SESIONES ACTIVAS");
        List<String> existingSessions = redisTemplate.opsForList().range("user:" + userId + ":sessions", 0, -1);
        if (existingSessions != null && existingSessions.contains(sessionId)) {
             System.out.println("YA HAY UNA SESION REGISTRADA PARA userId: " + userId + " sessionId: " + sessionId);
            return;
        }
        System.out.println("NO HAY UNA SESION PARA: " + userId + " REGISTRANDO EN REDIS AHORA...");
        redisTemplate.opsForList().rightPush("user:" + userId + ":sessions", sessionId);
        redisTemplate.opsForValue().set("session:" + sessionId + ":user", userId);

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            List<UserContact> contacts = userContactRepository.findByUser(userOpt.get());
            if(contacts.isEmpty()){
                System.out.println("ESTE USUARIO: " + userId + " NO TIENE CONTACTOS");
            }
            for (UserContact contact : contacts) {
                System.out.println("SUSCRIBIENDO CONTACTO: "+ contact.getId()+ " A LA SESSION: "+ sessionId);
                subscribeToContact(sessionId, contact.getContact().getId());
            }
        }else{
            System.out.println("USUARIO NO CONTRADO " + userId);
        }
        
    }

    public void handleSessionDisconnect(String sessionId) {
        // Fase 3: Limpieza
        System.out.println("Iniciando proceso de desconexión para sesión: " + sessionId);
        
        String userId = redisTemplate.opsForValue().get("session:" + sessionId + ":user");
        if (userId != null) {
            redisTemplate.opsForList().remove("user:" + userId + ":sessions", 0, sessionId);
            System.out.println("Sesión eliminada de la lista activa del usuario: " + userId);

            // Verificar si al usuario le quedan sesiones activas; si no, notificar desconexión
            List<String> remainingSessions = redisTemplate.opsForList().range("user:" + userId + ":sessions", 0, -1);
            if (remainingSessions == null || remainingSessions.isEmpty()) {
                notifyUserOffline(userId);
            }
        }

        List<String> subscriptions = redisTemplate.opsForList().range("session:" + sessionId + ":subscriptions", 0, -1);
        if (subscriptions != null) {
            for (String contactId : subscriptions) {
                redisTemplate.opsForList().remove("user:" + contactId + ":subscribers", 0, sessionId);
            }
            System.out.println("Suscripciones eliminadas. La sesión dejó de escuchar a " + subscriptions.size() + " contactos.");
        }
        redisTemplate.delete("session:" + sessionId + ":subscriptions");
        redisTemplate.delete("session:" + sessionId + ":user");
        System.out.println("Limpieza de Redis completada para la sesión.");
    }

    private void subscribeToContact(String sessionId, String contactId) {
        redisTemplate.opsForList().rightPush("user:" + contactId + ":subscribers", sessionId);
        redisTemplate.opsForList().rightPush("session:" + sessionId + ":subscriptions", contactId);
    }

    public void notifyUserOnline(String userId) {
        // Fase 2: Notificar a suscriptores
        List<String> subscribers = redisTemplate.opsForList().range("user:" + userId + ":subscribers", 0, -1);
        if (subscribers != null) {
            Map<String, String> payload = Map.of("type", "user_online", "userId", userId);
            Map<String, Object> headers = Map.of("type", "user_online");
            java.util.Set<String> notifiedUsers = new java.util.HashSet<>();
            for (String sessionId : subscribers) {
                // Recuperamos el userId asociado a la sesión desde Redis
                String subscriberId = redisTemplate.opsForValue().get("session:" + sessionId + ":user");
                
                if (subscriberId != null && !notifiedUsers.contains(subscriberId)) {
                    // Enviamos al destino específico: /queue/presence/{subscriberId}
                    // Esto coincide con la suscripción del cliente: /queue/presence/userId
                    System.out.println("ENVIADO NOTIFICICACION DE PRESENCIA: " + subscriberId);
                    messagingTemplate.convertAndSend("/queue/presence/" + subscriberId, payload, headers);
                    notifiedUsers.add(subscriberId);
                }
            }
        }else{
            System.out.println("LA LISTA DE SUSCRIPTORES ESTA VACIA " + subscribers.size());
        }
    }

    public void notifyUserOffline(String userId) {
        List<String> subscribers = redisTemplate.opsForList().range("user:" + userId + ":subscribers", 0, -1);
        if (subscribers != null) {
            Map<String, String> payload = Map.of("type", "user_offline", "userId", userId);
            Map<String, Object> headers = Map.of("type", "user_offline");
            java.util.Set<String> notifiedUsers = new java.util.HashSet<>();
            for (String sessionId : subscribers) {
                String subscriberId = redisTemplate.opsForValue().get("session:" + sessionId + ":user");
                
                if (subscriberId != null && !notifiedUsers.contains(subscriberId)) {
                    messagingTemplate.convertAndSend("/queue/presence/" + subscriberId, payload, headers);
                    notifiedUsers.add(subscriberId);
                }
            }
        }
    }
}
