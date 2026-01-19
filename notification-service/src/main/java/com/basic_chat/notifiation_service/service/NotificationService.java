package com.basic_chat.notifiation_service.service;

import java.util.List;
import java.util.Optional;

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

    public NotificationService(NotificationRepository notificationRepository, SimpMessagingTemplate messagingTemplate, 
                               UserContactRepository userContactRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
        this.userContactRepository = userContactRepository;
        this.userRepository = userRepository;
    }

    public void createNotification(Notification notification) {

        notificationRepository.save(notification);
    }

    public void deleteNotification(Long id) {
        notificationRepository.deleteById(id);
    }

    public void deleteAllNotificationsByUsername(String username) {
        notificationRepository.deleteAllByReceiver(username);
    }

    public List<Notification> getUnseenNotificationsByUsername(String username) {
        return notificationRepository.findByReceiverAndIsSeenFalse(username);

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

    public void addContact(String fromId, String contactUsername) {
        // Buscar o crear el usuario remitente (asumimos que fromId es el ID)
        User user = userRepository.findById(fromId)
                .orElseThrow(() -> new EntityNotFoundException("El usuario: " + fromId + " no se halla en el sistema"));
        // Buscar el contacto por username (ya que el cliente envía username)
        Optional<User> contactOpt = userRepository.findById(contactUsername);
        
        if (contactOpt.isPresent()) {
            User contactUser = contactOpt.get();
            
            UserContact userContact = new UserContact();
            userContact.setUser(user);
            userContact.setContact(contactUser);
            userContactRepository.save(userContact);
        } else {
            System.out.println("No se encontró usuario para el contacto: " + contactUsername);
        }
    }
}
