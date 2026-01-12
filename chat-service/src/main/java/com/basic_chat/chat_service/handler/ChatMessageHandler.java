package com.basic_chat.chat_service.handler;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.MessageSentEvent;
import com.basic_chat.chat_service.service.BlockService;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ChatMessageHandler implements WsMessageHandler{

    private final SessionManager sessionManager;
    private final MessageService messageService;
    private final RabbitTemplate rabbitTemplate;
    private final BlockService blockService;

    public ChatMessageHandler(SessionManager sessionManager, MessageService messageService, RabbitTemplate rabbitTemplate, BlockService blockService) {
        this.sessionManager = sessionManager;
        this.messageService = messageService;
        this.rabbitTemplate = rabbitTemplate;
        this.blockService = blockService;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasChatMessage();
    }

    @Override
    public void handle(SessionContext context, MessagesProto.WsMessage message) throws Exception {
        MessagesProto.ChatMessage chat = message.getChatMessage();
        String recipient = chat.getRecipient();

        // Verificar si el destinatario ha bloqueado al remitente
        if (blockService.isBlocked(chat.getSender(), recipient)) {
            // Si está bloqueado, no enviamos el mensaje ni lo guardamos.
            log.info("Mensaje bloqueado de {} para {}", chat.getSender(), recipient);
            sendBlockWarning(context.getSession(), recipient);
            return;
        }

        SessionManager.SessionInfo recipientSession =
                sessionManager.findByUsername(recipient);

        if (sessionManager.isUserOnline(recipient) && recipientSession != null) {
            recipientSession.getWsSession()
                    .sendMessage(new BinaryMessage(message.toByteArray()));
        } else {
            rabbitTemplate.convertAndSend("message.sent", new MessageSentEvent(chat.getSender(), chat.getRecipient()));
        }

        messageService.saveMessage(chat);
    }

    private void sendBlockWarning(WebSocketSession session, String recipient) {
        try {
            MessagesProto.ChatMessage warning = MessagesProto.ChatMessage.newBuilder()
                    .setSender("SISTEMA")
                    .setContent("No puedes enviar mensajes a " + recipient + ".")
                    .setTimestamp(System.currentTimeMillis())
                    .setType(MessagesProto.MessageType.ALERT)
                    .build();

            MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.newBuilder()
                    .setChatMessage(warning)
                    .build();

            session.sendMessage(new BinaryMessage(wsMessage.toByteArray()));
        } catch (Exception e) {
            log.error("Error enviando advertencia de bloqueo", e);
        }
    }
}
