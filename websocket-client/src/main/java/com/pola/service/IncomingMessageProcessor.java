package com.pola.service;

import com.pola.model.ChatMessage;
import com.pola.model.Contact;
import com.pola.model.Notification;
import com.pola.proto.MessagesProto;
import com.pola.proto.MessagesProto.WsMessage;
import com.pola.repository.MessageRepository;
import javafx.application.Platform;
import javafx.collections.ObservableList;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Procesa los mensajes entrantes del WebSocket.
 * Principio SOLID: Open/Closed - Fácil de extender con nuevos handlers en el mapa.
 */
public class IncomingMessageProcessor {
    private final MessageRepository messageRepository;
    private final ContactService contactService;
    private final ObservableList<ChatMessage> currentChatMessages;
    private final ObservableList<Notification> notifications;
    private final Supplier<Contact> currentContactSupplier;
    private final Supplier<String> currentUserIdSupplier;
    private final Map<WsMessage.PayloadCase, Consumer<WsMessage>> handlers = new HashMap<>();
    private Consumer<String> errorListener;

    public IncomingMessageProcessor(
            MessageRepository messageRepository,
            ContactService contactService,
            ObservableList<ChatMessage> currentChatMessages,
            ObservableList<Notification> notifications,
            Supplier<Contact> currentContactSupplier,
            Supplier<String> currentUserIdSupplier) {
        
        this.messageRepository = messageRepository;
        this.contactService = contactService;
        this.currentChatMessages = currentChatMessages;
        this.notifications = notifications;
        this.currentContactSupplier = currentContactSupplier;
        this.currentUserIdSupplier = currentUserIdSupplier;
        
        initializeHandlers();
    }

    public void setErrorListener(Consumer<String> listener) {
        this.errorListener = listener;
    }

    private void initializeHandlers() {
        handlers.put(WsMessage.PayloadCase.CHAT_MESSAGE_RESPONSE, this::handleMessageError);
        handlers.put(WsMessage.PayloadCase.UNREAD_MESSAGES_LIST, msg -> processUnreadMessages(msg.getUnreadMessagesList()));
        handlers.put(WsMessage.PayloadCase.DELETE_MESSAGE_REQUEST, msg -> processDeleteMessageRequest(msg.getDeleteMessageRequest()));
        handlers.put(WsMessage.PayloadCase.CLEAR_HISTORY_REQUEST, msg -> processClearHistoryRequest(msg.getClearHistoryRequest()));
        handlers.put(WsMessage.PayloadCase.CHAT_MESSAGE, this::handleChatMessage);
        handlers.put(WsMessage.PayloadCase.UNBLOCKED_USERS_LIST, msg -> processUnblockedUsersList(msg.getUnblockedUsersList()));
        handlers.put(WsMessage.PayloadCase.BLOCKED_USERS_LIST, msg -> processBlockedUsersList(msg.getBlockedUsersList()));
        handlers.put(WsMessage.PayloadCase.MESSAGES_READ_UPDATE, msg -> processMessagesReadUpdate(msg.getMessagesReadUpdate()));
    }

    public void process(WsMessage message) {
        Consumer<WsMessage> handler = handlers.get(message.getPayloadCase());
        if (handler != null) {
            handler.accept(message);
        } else {
            System.out.println("Tipo de mensaje no manejado: " + message.getPayloadCase());
        }
    }

    private void handleMessageError(WsMessage wsMessage) {
        MessagesProto.ChatMessageResponse response = wsMessage.getChatMessageResponse();
        String errorContent = response.getErrorMessage();
        
        if (response.getCause() == MessagesProto.FailureCause.BLOCKED) {
            String recipient = response.getRecipient();
            contactService.markUserAsBlockingMe(recipient);

            try {
                if (response.getMessageId() != null && !response.getMessageId().isEmpty()) {
                    long msgId = Long.parseLong(response.getMessageId());
                    messageRepository.delete(msgId);
                    Platform.runLater(() -> currentChatMessages.removeIf(m -> m.getId() == msgId));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Contact current = currentContactSupplier.get();
            if (current != null && current.getContactUsername().equals(recipient)) {
                Platform.runLater(() -> {
                    ChatMessage systemMessage = new ChatMessage(recipient, errorContent, "Sistema");
                    systemMessage.setId(System.currentTimeMillis());
                    currentChatMessages.add(systemMessage);
                });
            }
        } else if (errorListener != null) {
            Platform.runLater(() -> errorListener.accept(errorContent));
        }
    }

    private void handleChatMessage(WsMessage wsMessage) {
        MessagesProto.ChatMessage protobufMessage = wsMessage.getChatMessage();
        String senderId = protobufMessage.getSender();
        String content = protobufMessage.getContent();
        long messageId = Long.parseLong(protobufMessage.getId());
      
        try {
            if (messageRepository.existsById(messageId)) return;

            Contact contact = this.contactService.findContactByUsername(currentUserIdSupplier.get(), senderId)
                .orElseGet(() -> this.contactService.addContact(currentUserIdSupplier.get(), senderId));

            if(contact == null) return;

            ChatMessage localMessage = new ChatMessage(contact.getContactUsername(), content, senderId);
            localMessage.setId(messageId);
            ChatMessage saved = messageRepository.create(localMessage);

            Contact current = currentContactSupplier.get();
            if(current != null && current.getId() == contact.getId()){
                Platform.runLater(() -> currentChatMessages.add(saved));
                messageRepository.markAsRead(saved.getId());
            } else {
                updateNotification(senderId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void processDeleteMessageRequest(MessagesProto.DeleteMessageRequest request) {
        try {
            long messageId = Long.parseLong(request.getMessageId());
            messageRepository.delete(messageId);
            Platform.runLater(() -> currentChatMessages.removeIf(m -> m.getId() == messageId));
        } catch (Exception e) {
            System.err.println("Error al procesar eliminación: " + e.getMessage());
        }
    }

    private void processClearHistoryRequest(MessagesProto.ClearHistoryRequest request) {
        try {
            String senderUsername = request.getSender();
            messageRepository.deleteByContactUsername(senderUsername);
            
            Contact current = currentContactSupplier.get();
            if (current != null && current.getContactUsername().equals(senderUsername)) {
                Platform.runLater(() -> currentChatMessages.clear());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void processUnreadMessages(MessagesProto.UnreadMessagesList unreadMessagesList) {
        for (MessagesProto.ChatMessage protoMessage : unreadMessagesList.getMessagesList()) {
            String senderUsername = protoMessage.getSender();
            long messageId = Long.parseLong(protoMessage.getId());

            try {
                Contact contact = this.contactService.findContactByUsername(currentUserIdSupplier.get(), senderUsername)
                        .orElseGet(() -> this.contactService.addContact(currentUserIdSupplier.get(), senderUsername));

                if (contact == null || messageRepository.existsById(messageId)) continue;

                ChatMessage localMessage = new ChatMessage(contact.getContactUsername(), protoMessage.getContent(), senderUsername);
                localMessage.setId(messageId);
                ChatMessage saved = messageRepository.create(localMessage);

                Contact current = currentContactSupplier.get();
                if (current != null && current.getId() == contact.getId()) {
                    Platform.runLater(() -> currentChatMessages.add(saved));
                    messageRepository.markAsRead(saved.getId());
                } else {
                    updateNotification(senderUsername);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void processMessagesReadUpdate(MessagesProto.MessagesReadUpdate update) {
        List<String> idsStr = update.getMessageIdsList();
        if (idsStr.isEmpty()) return;

        List<Long> ids = new java.util.ArrayList<>();
        for(String s : idsStr) {
            try { ids.add(Long.parseLong(s)); } catch (NumberFormatException e) {}
        }

        try {
            messageRepository.markMultipleAsRead(ids);
            Platform.runLater(() -> {
                for (int i = 0; i < currentChatMessages.size(); i++) {
                    ChatMessage msg = currentChatMessages.get(i);
                    if (ids.contains(msg.getId())) {
                        msg.setRead(true);
                        currentChatMessages.set(i, msg);
                    }
                }
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void processBlockedUsersList(MessagesProto.BlockedUsersList list) {
        processUserStatusChange(list.getUsersList(), "Este usuario te ha bloqueado.", contactService::markUserAsBlockingMe);
    }

    private void processUnblockedUsersList(MessagesProto.UnblockedUsersList list) {
        processUserStatusChange(list.getUsersList(), "Este usuario te ha desbloqueado.", contactService::markUserAsUnblockingMe);
    }

    private void processUserStatusChange(List<String> users, String systemMsg, Consumer<String> action) {
        for (String username : users) {
            action.accept(username);
            try {
                ChatMessage localMessage = new ChatMessage(username, systemMsg, "Sistema");
                localMessage.setId(System.currentTimeMillis());
                messageRepository.create(localMessage);
                
                Contact current = currentContactSupplier.get();
                if (current != null && current.getContactUsername().equals(username)) {
                    Platform.runLater(() -> currentChatMessages.add(localMessage));
                } else {
                    updateNotification(username);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateNotification(String senderUsername) {
        Platform.runLater(() -> Notification.updateOrAdd(notifications, senderUsername));
    }
}