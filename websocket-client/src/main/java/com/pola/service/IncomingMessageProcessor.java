package com.pola.service;

import com.pola.model.ChatMessage;
import com.pola.model.Contact;
import com.pola.model.Notification;
import com.pola.proto.MessagesProto;
import com.pola.proto.MessagesProto.WsMessage;
import com.pola.util.MessageProcessingContext;
import javafx.application.Platform;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Procesa los mensajes entrantes del WebSocket.
 * Principio SOLID: Open/Closed - Fácil de extender con nuevos handlers en el mapa.
 */
public class IncomingMessageProcessor {
    private final MessageProcessingContext context;
    private final Map<WsMessage.PayloadCase, Consumer<WsMessage>> handlers = new HashMap<>();
    private Consumer<String> errorListener;

    public IncomingMessageProcessor(MessageProcessingContext context) {
        this.context = context;
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
        handlers.put(WsMessage.PayloadCase.CONTACT_IDENTITY, msg -> processContactIdentity(msg.getContactIdentity()));
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
            context.getContactService().markUserAsBlockingMe(recipient);

            try {
                if (response.getMessageId() != null && !response.getMessageId().isEmpty()) {
                    long msgId = Long.parseLong(response.getMessageId());
                    context.getMessageRepository().delete(msgId);
                    Platform.runLater(() -> context.getCurrentChatMessages().removeIf(m -> m.getId() == msgId));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Contact current = context.getCurrentContactSupplier().get();
            if (current != null && current.getContactUsername().equals(recipient)) {
                Platform.runLater(() -> {
                    ChatMessage systemMessage = new ChatMessage(recipient, errorContent, "Sistema");
                    systemMessage.setId(System.currentTimeMillis());
                    context.getCurrentChatMessages().add(systemMessage);
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
            if (context.getMessageRepository().existsById(messageId)) return;

            Contact contact = context.getContactService().findContactByUsername(context.getCurrentUserIdSupplier().get(), senderId)
            //añade un contacto "improvisado" con un id no oficial
                .orElseGet(() -> context.getContactService().addContact(context.getCurrentUserIdSupplier().get(), senderId, false));

            if(contact == null) return;

            ChatMessage localMessage = new ChatMessage(contact.getContactUsername(), content, senderId);
            localMessage.setId(messageId);
            ChatMessage saved = context.getMessageRepository().create(localMessage);

            Contact current = context.getCurrentContactSupplier().get();
            if(current != null && current.getId() == contact.getId()){
                Platform.runLater(() -> context.getCurrentChatMessages().add(saved));
                context.getMessageRepository().markAsRead(saved.getId());
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
            context.getMessageRepository().delete(messageId);
            Platform.runLater(() -> context.getCurrentChatMessages().removeIf(m -> m.getId() == messageId));
        } catch (Exception e) {
            System.err.println("Error al procesar eliminación: " + e.getMessage());
        }
    }
    /**
     * Elimina los mensajes de un usuario de la db local.
     * Limpia la ui de chat de todos los mensajes.
     * 
     */
    private void processClearHistoryRequest(MessagesProto.ClearHistoryRequest request) {
        try {
            String senderUsername = request.getSender();
            context.getMessageRepository().deleteByContactUsername(senderUsername);
            // Verifica si el contacto actual es igual al de la peticion
            Contact current = context.getCurrentContactSupplier().get();
            if (current != null && current.getContactUsername().equals(senderUsername)) {
                // Limpia la ui de los mensajes.
                Platform.runLater(() -> context.getCurrentChatMessages().clear());
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
                Contact contact = context.getContactService().findContactByUsername(context.getCurrentUserIdSupplier().get(), senderUsername)
                        .orElseGet(() -> context.getContactService().addContact(context.getCurrentUserIdSupplier().get(), senderUsername, false));

                if (contact == null || context.getMessageRepository().existsById(messageId)) continue;

                ChatMessage localMessage = new ChatMessage(contact.getContactUsername(), protoMessage.getContent(), senderUsername);
                localMessage.setId(messageId);
                ChatMessage saved = context.getMessageRepository().create(localMessage);

                Contact current = context.getCurrentContactSupplier().get();
                if (current != null && current.getId() == contact.getId()) {
                    Platform.runLater(() -> context.getCurrentChatMessages().add(saved));
                    context.getMessageRepository().markAsRead(saved.getId());
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
            context.getMessageRepository().markMultipleAsRead(ids);
            Platform.runLater(() -> {
                for (int i = 0; i < context.getCurrentChatMessages().size(); i++) {
                    ChatMessage msg = context.getCurrentChatMessages().get(i);
                    if (ids.contains(msg.getId())) {
                        msg.setRead(true);
                        context.getCurrentChatMessages().set(i, msg);
                    }
                }
            });
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void processBlockedUsersList(MessagesProto.BlockedUsersList list) {
        processUserStatusChange(list.getUsersList(), "Este usuario te ha bloqueado.", context.getContactService()::markUserAsBlockingMe);
    }

    private void processUnblockedUsersList(MessagesProto.UnblockedUsersList list) {
        processUserStatusChange(list.getUsersList(), "Este usuario te ha desbloqueado.", context.getContactService()::markUserAsUnblockingMe);
    }
    /**
     * Actualizara el id temporal por el id oficial del remitente.
     * @param identity El .proto con el id del remitente y su username
     */
    private void processContactIdentity(MessagesProto.ContactIdentity identity) {
        String remoteUserId = identity.getSenderId();
        String senderUsername = identity.getSenderUsername();

        if(senderUsername != null && !senderUsername.isEmpty()){
            // Verificar si es la primera vez que obtenemos el ID de este contacto (era null o vacío)
            boolean isFirstIdUpdate = context.getContactService().findContactByUsername(context.getCurrentUserIdSupplier().get(), senderUsername)
                .map(c -> c.getContactUserId() == null || c.getContactUserId().isEmpty())
                .orElse(true);

            context.getContactService().updateContactId(senderUsername, remoteUserId);
            
            // Marcar como conectado inmediatamente ya que acabamos de recibir señal de vida
            context.getContactService().setContactOnline(remoteUserId, true);

            // Si es la primera vez que tenemos su ID, enviamos el nuestro de vuelta para completar el handshake
            if (isFirstIdUpdate) {
                context.getMessageSender().sendContactIdentity(context.getCurrentUserIdSupplier().get(), context.getCurrentUsernameSupplier().get(), senderUsername);
            }

            // Añadir notificación a la bandeja en lugar de mensaje de chat
            updateNotification(senderUsername);
        }
        // Si el contacto era "improvisado" (sin ID), ahora tiene ID.
        // Devolvemos nuestro ID para completar el handshake si es necesario.
        // if (contactWasTemporary) {
        //     messageSender.sendContactIdentity(senderUsername, currentUserIdSupplier.get());
        // }
    }

    private void processUserStatusChange(List<String> users, String systemMsg, Consumer<String> action) {
        for (String username : users) {
            action.accept(username);
            try {
                ChatMessage localMessage = new ChatMessage(username, systemMsg, "Sistema");
                localMessage.setId(System.currentTimeMillis());
                context.getMessageRepository().create(localMessage);
                
                Contact current = context.getCurrentContactSupplier().get();
                if (current != null && current.getContactUsername().equals(username)) {
                    Platform.runLater(() -> context.getCurrentChatMessages().add(localMessage));
                } else {
                    updateNotification(username);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateNotification(String senderUsername) {
        Platform.runLater(() -> Notification.updateOrAdd(context.getNotifications(), senderUsername));
    }
}