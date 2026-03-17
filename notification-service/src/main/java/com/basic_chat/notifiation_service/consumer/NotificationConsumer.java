
/**
 * NotificationConsumer es responsable de recibir y procesar eventos provenientes de otros servicios
 * a través de RabbitMQ. Actúa como consumidor de eventos clave (mensajes enviados, mensajes leídos,
 * contactos añadidos, usuarios en línea) y delega la lógica correspondiente a los servicios de notificación
 * y presencia de usuario. De esta manera, integra el servicio de notificaciones con los flujos core de la aplicación.
 */
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
    }
}
