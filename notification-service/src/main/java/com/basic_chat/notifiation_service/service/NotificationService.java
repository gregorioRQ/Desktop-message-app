package com.basic_chat.notifiation_service.service;

import java.util.List;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.notifiation_service.model.Notification;
import com.basic_chat.notifiation_service.repository.NotificationRepository;

@Service
@Transactional
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(NotificationRepository notificationRepository, SimpMessagingTemplate messagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.messagingTemplate = messagingTemplate;
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
        messagingTemplate.convertAndSend(destination, event);
    }

    public void notifyMessageSeen(Notification event) {
        // Enviar al destino STOMP específico de ese usuario
        String destination = "/topic/seen/" + event.getReceiver();
        messagingTemplate.convertAndSend(destination, event);
    }
}
