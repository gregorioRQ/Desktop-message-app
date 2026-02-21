
/**
 * NotificationConsumer es responsable de recibir y procesar eventos provenientes de otros servicios
 * a través de RabbitMQ. Actúa como consumidor de eventos clave (mensajes enviados, mensajes leídos,
 * contactos añadidos, usuarios en línea) y delega la lógica correspondiente a los servicios de notificación
 * y presencia de usuario. De esta manera, integra el servicio de notificaciones con los flujos core de la aplicación.
 */
package com.basic_chat.notifiation_service.consumer;

import com.basic_chat.notifiation_service.model.Notification;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.basic_chat.notifiation_service.model.ContactAddEvent;
import com.basic_chat.notifiation_service.model.MessageSeenEvent;
import com.basic_chat.notifiation_service.model.MessageSentEvent;
import com.basic_chat.notifiation_service.model.UserOnlineEvent;
import com.basic_chat.notifiation_service.service.NotificationService;
import com.basic_chat.notifiation_service.service.UserPresenceService;

@Component
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final UserPresenceService userPresenceService;

    public NotificationConsumer(NotificationService notificationService, UserPresenceService userPresenceService) {
        this.notificationService = notificationService;
        this.userPresenceService = userPresenceService;
    }
    /**
     * Listener que se ejecuta cuando un usuario añade como contacto a otro.
     * @param event El evento con los datos para procesar la notificacion.
     */
    @RabbitListener(queues = "contact.events")
    public void handleContactAdded(ContactAddEvent event) {
        notificationService.addContact(event.getFrom(), event.getTo());
    }

    /**
     * Listener para eventos de mensajes enviados.
     * 
     * @param event El evento publicado por el servicio de chat.
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

    /**
     * Listener para eventos de mensajes vistos.
     * @param event El evento publicado por el servicio de chat.
     */
    @RabbitListener(queues = "message.read")
    public void handleMessageReadEvent(MessageSeenEvent event) {
        Notification notification = new Notification();
        notification.setReceiver(event.getReceiver());
        notification.setMessage(event.getReceiver() + " ha leído tus mensajes.");
        notification.setType("MESSAGES READ");
        notification.setSeen(true); 
        notificationService.notifyMessageSeen(notification);
    }

    /**
     * Listener para eventos de tipo usuario en linea.
     * @param event El evento publicado por el cliente.
     */
    @RabbitListener(queues = "user.online")
    public void handleUserOnlineEvent(UserOnlineEvent event) {
        userPresenceService.notifyUserOnline(event.getUserId());
    }
}
