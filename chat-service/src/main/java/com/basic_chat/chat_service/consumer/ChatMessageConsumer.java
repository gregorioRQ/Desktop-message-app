package com.basic_chat.chat_service.consumer;

import com.basic_chat.chat_service.models.RoutedMessageEvent;
import com.basic_chat.chat_service.service.DeliveryService;
import com.basic_chat.proto.MessagesProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ChatMessageConsumer {

    private final DeliveryService deliveryService;
    private final String instanceId;

    public ChatMessageConsumer(
            DeliveryService deliveryService,
            @Value("${chat.service.instance.id:instance-1}") String instanceId) {
        this.deliveryService = deliveryService;
        this.instanceId = instanceId;
    }

    @RabbitListener(queues = "message.sent." + "${chat.service.instance.id:instance-1}")
    public void handleRoutedMessage(RoutedMessageEvent event) {
        log.debug("Received message from {} to {} in instance {}",
                event.getSender(), event.getRecipient(), instanceId);

        try {
            MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.parseFrom(event.getContent());

            if (wsMessage.hasChatMessage()) {
                MessagesProto.ChatMessage chatMessage = wsMessage.getChatMessage();
                
                log.debug("Processing chat message ID: {}", chatMessage.getId());
                
                deliveryService.processMessage(wsMessage, chatMessage);
            }
        } catch (Exception e) {
            log.error("Error processing routed message from {} to {}: {}",
                    event.getSender(), event.getRecipient(), e.getMessage());
        }
    }
}
