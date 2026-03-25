package com.basic_chat.notifiation_service.consumer;

import com.basic_chat.notifiation_service.model.NotificationEvent;
import com.basic_chat.notifiation_service.service.SseNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de eventos de notificación desde RabbitMQ.
 * 
 * Este componente escucha la cola "message.notification" donde connection-service
 * publica eventos cuando un usuario recibe un mensaje mientras está offline.
 * 
 * La responsabilidad es enviar una notificación SSE al cliente destinatario.
 */
@Component
public class NotificationConsumer {

    private static final Logger logger = LoggerFactory.getLogger(NotificationConsumer.class);

    private static final String NEW_MESSAGE_NOTIFICATION = "nuevo mensaje";

    private final SseNotificationService sseNotificationService;

    public NotificationConsumer(SseNotificationService sseNotificationService) {
        this.sseNotificationService = sseNotificationService;
    }

    /**
     * Procesa un evento de nuevo mensaje recibido desde connection-service.
     * 
     * Este método se ejecuta cuando connection-service publica un evento en la cola
     * message.notification (cuando el destinatario está offline).
     * 
     * El evento contiene el recipientUserId que se usa directamente para enviar
     * la notificación SSE al cliente.
     * 
     * @param event Evento de notificación contendo recipientUserId
     */
    @RabbitListener(queues = "message.notification")
    public void handleNotificationEvent(NotificationEvent event) {
        logger.info("Received notification event: type={}, sender={}, recipient={}, recipientUserId={}",
                event.getType(), event.getSender(), event.getRecipient(), event.getRecipientUserId());
        
        String recipientUserId = event.getRecipientUserId();
        
        if (recipientUserId != null && !recipientUserId.isEmpty()) {
            boolean sentViaSse = sseNotificationService.sendNotification(recipientUserId, NEW_MESSAGE_NOTIFICATION);
            
            if (sentViaSse) {
                logger.debug("Notification sent via SSE to userId: {}", recipientUserId);
            } else {
                logger.warn("No active SSE connection for userId: {}. Notification not sent.", recipientUserId);
            }
        } else {
            logger.warn("Notification event missing recipientUserId. Cannot send notification.");
        }
    }
}
