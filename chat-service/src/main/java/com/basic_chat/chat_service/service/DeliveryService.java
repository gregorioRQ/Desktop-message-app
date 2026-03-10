package com.basic_chat.chat_service.service;

import com.basic_chat.chat_service.models.DeliveryStatusEvent;
import com.basic_chat.chat_service.models.RoutedMessageEvent;
import com.basic_chat.proto.MessagesProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DeliveryService {

    private final MessageService messageService;
    private final BlockService blockService;
    private final RabbitTemplate rabbitTemplate;
    private final String instanceId;

    public DeliveryService(
            MessageService messageService,
            BlockService blockService,
            RabbitTemplate rabbitTemplate,
            @Value("${chat.service.instance.id:instance-1}") String instanceId) {
        this.messageService = messageService;
        this.blockService = blockService;
        this.rabbitTemplate = rabbitTemplate;
        this.instanceId = instanceId;
    }

    public void processMessage(MessagesProto.WsMessage wsMessage, MessagesProto.ChatMessage chatMessage) {
        String sender = chatMessage.getSender();
        String recipient = chatMessage.getRecipient();

        if (isBlocked(sender, recipient)) {
            log.warn("Blocked message from {} to {}", sender, recipient);
            sendDeliveryStatus(recipient, "BLOCKED", chatMessage.getId(), chatMessage.toByteArray());
            return;
        }

        saveMessage(chatMessage);
        sendDeliveryStatus(recipient, "DELIVERED", chatMessage.getId(), chatMessage.toByteArray());
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

    private void sendDeliveryStatus(String recipient, String type, String messageId, byte[] messageData) {
        try {
            DeliveryStatusEvent event = new DeliveryStatusEvent();
            event.setType(type);
            event.setMessageId(messageId);
            event.setRecipient(recipient);
            event.setData(messageData);

            rabbitTemplate.convertAndSend("message.exchange", "delivery", event);
            log.debug("Delivery status {} sent for message {} to {}", type, messageId, recipient);
        } catch (Exception e) {
            log.error("Failed to send delivery status for message {}: {}", messageId, e.getMessage());
        }
    }
}
