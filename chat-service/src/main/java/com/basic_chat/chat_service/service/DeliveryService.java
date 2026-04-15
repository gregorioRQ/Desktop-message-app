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
     * 3. Notifica a connection-service sobre el estado de entrega
     * 
     * @param wsMessage Mensaje completo WsMessage
     * @param chatMessage Mensaje de chat parsed
     */
    public void processMessage(MessagesProto.WsMessage wsMessage, MessagesProto.ChatMessage chatMessage) {
        String sender = chatMessage.getSender();
        String recipient = chatMessage.getRecipient();

        if (isBlocked(sender, recipient)) {
            log.warn("Blocked message from {} to {}", sender, recipient);
            return;
        }

        saveMessage(chatMessage);
        sendDeliveryStatus(wsMessage, chatMessage.getId(), recipient, "DELIVERED");
        log.debug("Message processed and saved: ID={}, from={}, to={}", 
                chatMessage.getId(), sender, recipient);
    }

    private void sendDeliveryStatus(MessagesProto.WsMessage wsMessage, String messageId, String recipient, String type) {
        try {
            DeliveryStatusEvent event = new DeliveryStatusEvent();
            event.setType(type);
            event.setMessageId(messageId);
            event.setRecipient(recipient);
            event.setData(wsMessage.toByteArray());
            
            rabbitTemplate.convertAndSend(
                RabbitMQconfig.MESSAGE_EXCHANGE,
                "delivery",
                event
            );
            log.info("Delivery status {} sent for message {} to {}", type, messageId, recipient);
        } catch (Exception e) {
            log.error("Failed to send delivery status for message {}: {}", messageId, e.getMessage());
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
