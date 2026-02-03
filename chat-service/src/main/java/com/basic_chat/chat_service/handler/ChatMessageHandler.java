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
public class ChatMessageHandler implements WsMessageHandler {

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
    public void handle(SessionContext context, MessagesProto.WsMessage message) {
        MessagesProto.ChatMessage chat = message.getChatMessage();
        log.debug("Processing chat message ID: {}", chat.getId());
        try {
            processChatMessage(context, message, chat);
        } catch (Exception e) {
            log.error("Unhandled exception while processing chat message ID: {}", chat.getId(), e);
        }
    }

    private void processChatMessage(SessionContext context, MessagesProto.WsMessage wsMessage, MessagesProto.ChatMessage chatMessage) {
        if (isBlocked(chatMessage.getSender(), chatMessage.getRecipient())) {
            handleBlockedMessage(context.getSession(), chatMessage);
            return;
        }

        deliverMessage(wsMessage, chatMessage);
        saveMessage(chatMessage);
    }

    private boolean isBlocked(String sender, String recipient) {
        try {
            return blockService.isBlocked(sender, recipient);
        } catch (Exception e) {
            log.error("Error checking block status between {} and {}", sender, recipient, e);
            return false;
        }
    }

    private void handleBlockedMessage(WebSocketSession session, MessagesProto.ChatMessage chatMessage) {
        log.warn("Blocked message from {} to {}", chatMessage.getSender(), chatMessage.getRecipient());
        sendBlockErrorResponse(session, chatMessage.getId(), chatMessage.getRecipient());
    }
    
    private void deliverMessage(MessagesProto.WsMessage message, MessagesProto.ChatMessage chat) {
        try {
            SessionManager.SessionInfo recipientSession = sessionManager.findByUsername(chat.getRecipient());
            if (sessionManager.isUserOnline(chat.getRecipient()) && recipientSession != null) {
                sendRealTimeMessage(recipientSession.getWsSession(), message);
            } else {
                queueOfflineMessage(chat);
            }
        } catch (Exception e) {
            log.error("Failed to deliver message ID: {}", chat.getId(), e);
        }
    }

    private void sendRealTimeMessage(WebSocketSession recipientSession, MessagesProto.WsMessage message) {
        try {
            recipientSession.sendMessage(new BinaryMessage(message.toByteArray()));
            log.info("Message ID: {} delivered in real-time to {}", message.getChatMessage().getId(), recipientSession.getAttributes().get("username"));
        } catch (Exception e) {
            log.error("Failed to send real-time message ID: {} to session {}", message.getChatMessage().getId(), recipientSession.getId(), e);
        }
    }

    private void queueOfflineMessage(MessagesProto.ChatMessage chat) {
        try {
            rabbitTemplate.convertAndSend("message.sent", new MessageSentEvent(chat.getSender(), chat.getRecipient()));
            log.info("User {} is offline. Message ID: {} queued for later delivery.", chat.getRecipient(), chat.getId());
        } catch (Exception e) {
            log.error("Failed to queue offline message ID: {} for user {}", chat.getId(), chat.getRecipient(), e);
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
    
    private void sendBlockErrorResponse(WebSocketSession session, String messageId, String recipient) {
        try {
            MessagesProto.ChatMessageResponse response = MessagesProto.ChatMessageResponse.newBuilder()
                    .setMessageId(messageId)
                    .setSuccess(false)
                    .setCause(MessagesProto.FailureCause.BLOCKED)
                    .setErrorMessage("No puedes enviar mensajes a " + recipient + ".")
                    .setRecipient(recipient)
                    .build();

            MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.newBuilder()
                    .setChatMessageResponse(response)
                    .build();

            session.sendMessage(new BinaryMessage(wsMessage.toByteArray()));
            log.info("Block error response sent for message ID: {} to session {}", messageId, session.getId());
        } catch (Exception e) {
            log.error("Error sending block error response for message ID: {} to session {}", messageId, session.getId(), e);
        }
    }
}
