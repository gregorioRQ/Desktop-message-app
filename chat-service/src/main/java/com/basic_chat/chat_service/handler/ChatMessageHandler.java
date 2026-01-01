package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto;

@Component
public class ChatMessageHandler implements WsMessageHandler{

    private final SessionManager sessionManager;
    private final MessageService messageService;

    public ChatMessageHandler(SessionManager sessionManager, MessageService messageService) {
        this.sessionManager = sessionManager;
        this.messageService = messageService;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasChatMessage();
    }

    @Override
    public void handle(SessionContext context, MessagesProto.WsMessage message) throws Exception {
        MessagesProto.ChatMessage chat = message.getChatMessage();
        String recipient = chat.getRecipient();

        SessionManager.SessionInfo recipientSession =
                sessionManager.findByUsername(recipient);

        if (sessionManager.isUserOnline(recipient) && recipientSession != null) {
            recipientSession.getWsSession()
                    .sendMessage(new BinaryMessage(message.toByteArray()));
        }

        messageService.saveMessage(chat);
    }

}
