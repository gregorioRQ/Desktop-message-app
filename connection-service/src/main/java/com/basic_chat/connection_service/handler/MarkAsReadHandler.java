package com.basic_chat.connection_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MarkAsReadHandler implements ConnectionWsMessageHandler {

    private final MessageRouterService messageRouterService;

    public MarkAsReadHandler(MessageRouterService messageRouterService) {
        this.messageRouterService = messageRouterService;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasMarkMessagesAsReadRequest();
    }

    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.MarkMessagesAsReadRequest request = message.getMarkMessagesAsReadRequest();
        String recipient = request.getRecipient();

        log.info("Procesando mark as read de {} para destinatario {}", sender, recipient);

        try {
            byte[] messageData = message.toByteArray();
            messageRouterService.routeMessage(sender, recipient, messageData);
        } catch (Exception e) {
            log.error("Error al procesar mark as read: {}", e.getMessage(), e);
        }
    }
}
