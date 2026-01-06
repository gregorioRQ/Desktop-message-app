package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.DeleteMessageRequest;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DeleteMessageHandler implements WsMessageHandler{
    private final MessageService messageService;

    public DeleteMessageHandler(MessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasDeleteMessageRequest();
    }

    @Override
    public void handle(SessionContext context, MessagesProto.WsMessage message) throws Exception {
        DeleteMessageRequest request = message.getDeleteMessageRequest();
        log.debug("Procesando solicitud de eliminación para mensaje ID: {}", request.getMessageId());
        
        // El servicio se encarga de la validación y eliminación
        messageService.deleteMessage(request);
        
        log.info("Mensaje eliminado correctamente: {}", request.getMessageId());
    }
}
