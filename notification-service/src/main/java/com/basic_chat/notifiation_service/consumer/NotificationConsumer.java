package com.basic_chat.notifiation_service.consumer;

import com.basic_chat.notifiation_service.model.Notification;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.basic_chat.notifiation_service.model.ContactAddEvent;
import com.basic_chat.notifiation_service.model.MessageSeenEvent;
import com.basic_chat.notifiation_service.model.MessageSentEvent;
import com.basic_chat.notifiation_service.service.NotificationService;

@Component
public class NotificationConsumer {

    private final NotificationService notificationService;

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "contact.events")
    public void handleContactAdded(ContactAddEvent event) {

        Notification ntn = new Notification();
        ntn.setType("CONTACT ADD");
        ntn.setSeen(false);
        ntn.setMessage(event.getFrom() + " te añadió como contacto");
        ntn.setSender(event.getFrom());
        ntn.setReceiver(event.getTo());
        notificationService.createNotification(ntn);
    }

    /**
     * Listener para eventos de mensajes enviados.
     * 
     * @param event
     */
    @RabbitListener(queues = "message.sent")
    public void handleMessageSentEvent(MessageSentEvent event) {

        // guarda la notificación en la base de datos
        Notification notification = new Notification();
        notification.setSender(event.getSender());
        notification.setReceiver(event.getReceiver());
        notification.setMessage("Tienes un nuevo mensaje de: " + event.getSender());
        notification.setType("MESSAGE_SENT");
        notification.setSeen(false);
        // Notifica al usuario a través de WebSocket
        notificationService.notifyUser(notification);
        // notificationService.createNotification(notification);
    }

    @RabbitListener(queues = "message.read")
    public void handleMessageReadEvent(MessageSeenEvent event) {
        Notification notification = new Notification();
        notification.setReceiver(event.getReceiver());
        notification.setMessage(event.getReceiver() + " ha leído tus mensajes.");
        notification.setType("MESSAGES READ");
        notification.setSeen(true); // No es necesario notificar visualmente

        // notifica al usuario acerca del mensaje visto.
        notificationService.notifyMessageSeen(notification);
    }
}
