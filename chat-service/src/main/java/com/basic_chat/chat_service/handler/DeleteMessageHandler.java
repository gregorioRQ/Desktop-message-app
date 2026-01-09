package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.DeleteMessageRequest;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DeleteMessageHandler implements WsMessageHandler{
    private final MessageService messageService;
    private final SessionManager sessionManager;

    public DeleteMessageHandler(MessageService messageService, SessionManager sessionManager) {
        this.messageService = messageService;
        this.sessionManager = sessionManager;
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
        Message deletedMessage = messageService.deleteMessage(request);
        
        log.info("Mensaje eliminado correctamente: {}", request.getMessageId());

        // Notificar al receptor si está en línea
        String recipient = deletedMessage.getToUserId();
        SessionManager.SessionInfo recipientSession = sessionManager.findByUsername(recipient);

        if (sessionManager.isUserOnline(recipient) && recipientSession != null) {
            recipientSession.getWsSession()
                    .sendMessage(new BinaryMessage(message.toByteArray()));
            log.debug("Notificación de eliminación enviada a {}", recipient);
        }
    }
}
