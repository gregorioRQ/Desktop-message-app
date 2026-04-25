package com.basic_chat.chat_service.service;

import com.basic_chat.chat_service.config.RabbitMQconfig;
import com.basic_chat.chat_service.models.DeliveryStatusEvent;
import com.basic_chat.proto.MessagesProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DeliveryService {

    private final MessageService messageService;
    private final BlockService blockService;
    private final RabbitTemplate rabbitTemplate;

    public DeliveryService(MessageService messageService, BlockService blockService, RabbitTemplate rabbitTemplate) {
        this.messageService = messageService;
        this.blockService = blockService;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Procesa un mensaje de chat.
     *
     * Este método:
     * 1. Verifica si el remitente está bloqueado por el destinatario
     * 2. Guarda el mensaje en la base de datos
     * 3. Notifica al remitente que el mensaje fue entregado al destinatario
     *
     * @param wsMessage Mensaje completo WsMessage
     * @param chatMessage Mensaje de chat parsed
     */
    public void processMessage(MessagesProto.WsMessage wsMessage, MessagesProto.ChatMessage chatMessage) {
        String sender = chatMessage.getSender();
        String recipient = chatMessage.getRecipient();
        String messageId = chatMessage.getId();

        if (isBlocked(sender, recipient)) {
            log.warn("Blocked message from {} to {}", sender, recipient);
            return;
        }

        saveMessage(chatMessage);

        // Enviar notificación de entrega al REMITENTE (no al destinatario)
        sendDeliveredNotificationToSender(messageId, sender, recipient);

        log.debug("Message processed and saved: ID={}, from={}, to={}", chatMessage.getId(), sender, recipient);
    }

    /**
     * Envía una notificación de entrega al remitente del mensaje.
     *
     * Cuando el mensaje es guardado exitosamente en la base de datos del chat-service,
     * se notifica al remitente que su mensaje fue entregado al destinatario.
     *
     * @param messageId ID del mensaje entregado
     * @param sender Username del remitente (quién envió el mensaje original)
     * @param recipient Username del destinatario (quién recibió el mensaje)
     */
    private void sendDeliveredNotificationToSender(String messageId, String sender, String recipient) {
        try {
            // Crear el mensaje de notificación de entrega
            MessagesProto.MessageDeliveredUpdate deliveredUpdate = MessagesProto.MessageDeliveredUpdate.newBuilder()
                    .addMessageIds(messageId)
                    .setDeliveredToUsername(recipient)
                    .build();

            MessagesProto.WsMessage notificationMessage = MessagesProto.WsMessage.newBuilder()
                    .setMessageDeliveredUpdate(deliveredUpdate)
                    .build();

            DeliveryStatusEvent event = new DeliveryStatusEvent();
            event.setType("DELIVERED");
            event.setMessageId(messageId);
            event.setRecipient(sender); // El remitente recibe la notificación
            event.setData(notificationMessage.toByteArray());

            rabbitTemplate.convertAndSend(
                    RabbitMQconfig.MESSAGE_EXCHANGE,
                    "delivery",
                    event
            );
            log.info("Delivered notification sent for message {} to sender {}", messageId, sender);
        } catch (Exception e) {
            log.error("Failed to send delivered notification for message {}: {}", messageId, e.getMessage());
        }
    }

    private boolean isBlocked(String sender, String recipient) {
        try {
            return blockService.isBlocked(sender, recipient);
        } catch (Exception e) {
            log.error("Error checking block status between {} and {}", sender, recipient, e);
            return false;
        }
    }

    private void saveMessage(MessagesProto.ChatMessage chatMessage) {
        try {
            messageService.saveMessage(chatMessage);
            log.debug("Message ID: {} saved to database.", chatMessage.getId());
        } catch (Exception e) {
            log.error("Failed to save message ID: {}", chatMessage.getId(), e);
        }
    }
}
