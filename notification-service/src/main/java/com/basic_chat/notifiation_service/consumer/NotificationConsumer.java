package com.basic_chat.notifiation_service.consumer;

import com.basic_chat.notifiation_service.model.ContactAddEvent;
import com.basic_chat.notifiation_service.model.DeliveryEvent;
import com.basic_chat.notifiation_service.model.Notification;
import com.basic_chat.notifiation_service.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);

    private final NotificationService notificationService;

    public NotificationConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "contact.events")
    public void handleContactAdded(ContactAddEvent event) {
        logger.info("Received contact add event: {} added {}", event.getFrom(), event.getTo());
        notificationService.addContact(event.getFrom(), event.getTo());
    }

<<<<<<< Updated upstream
    /**
     * Listener para eventos de mensajes enviados.
     * 
     * @param event
     */
    @RabbitListener(queues = "message.sent")
    public void handleMessageSentEvent(MessageSentEvent event) {
        Notification notification = new Notification();
        notification.setSender(event.getSender());
        notification.setReceiver(event.getReceiver());
        notification.setMessage("Tienes un nuevo mensaje de: " + event.getSender());
        notification.setType("MESSAGE_SENT");
        notification.setSeen(false);
        notificationService.notifyUser(notification);
    }

    @RabbitListener(queues = "message.read")
    public void handleMessageReadEvent(MessageSeenEvent event) {
        Notification notification = new Notification();
        notification.setReceiver(event.getReceiver());
        notification.setMessage(event.getReceiver() + " ha leído tus mensajes.");
        notification.setType("MESSAGES READ");
        notification.setSeen(true); 
        notificationService.notifyMessageSeen(notification);
    }

    @RabbitListener(queues = "user.online")
    public void handleUserOnlineEvent(UserOnlineEvent event) {
        userPresenceService.notifyUserOnline(event.getUserId());
=======
    @RabbitListener(queues = "message.delivery")
    public void handleDeliveryEvent(DeliveryEvent event) {
        logger.info("Received delivery event: type={}, messageId={}, recipient={}",
                event.getType(), event.getMessageId(), event.getRecipient());

        if ("DELIVERED".equals(event.getType())) {
            Notification notification = new Notification();
            notification.setReceiver(event.getRecipient());
            notification.setMessage("Nuevo mensaje recibido");
            notification.setType("MESSAGE_SENT");
            notification.setSeen(false);
            notificationService.notifyUser(notification);
        }
>>>>>>> Stashed changes
    }
}
