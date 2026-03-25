package com.basic_chat.connection_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DeleteMessageHandler implements ConnectionWsMessageHandler {

    private final MessageRouterService messageRouterService;

    public DeleteMessageHandler(MessageRouterService messageRouterService) {
        this.messageRouterService = messageRouterService;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasDeleteMessageRequest();
    }

    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.DeleteMessageRequest deleteRequest = message.getDeleteMessageRequest();
        String messageId = deleteRequest.getMessageId();
        String recipient = deleteRequest.getRecipient();
        
        log.info("Procesando solicitud de eliminación de mensaje {} por usuario {} para destinatario {}", messageId, sender, recipient);
        
        try {
            byte[] messageData = message.toByteArray();
            messageRouterService.routeDeletionNotification(sender, recipient, messageId, messageData);
            
        } catch (Exception e) {
            log.error("Error al procesar solicitud de eliminación de mensaje {}: {}", messageId, e.getMessage(), e);
        }
    }
}
