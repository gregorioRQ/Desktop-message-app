package com.pola.service;

import com.pola.model.ChatMessage;
import com.pola.proto.MessagesProto;
import com.pola.proto.MessagesProto.MessageType;
import com.pola.proto.MessagesProto.WsMessage;
import java.time.Instant;
import java.util.List;

/**
 * Responsable de construir y enviar mensajes Protobuf.
 * Principio SOLID: Single Responsibility - Solo maneja el envío de mensajes.
 */
public class MessageSender {
    private final WebSocketService webSocketService;

    public MessageSender(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    public void sendTextMessage(long id, String content, String sender, String recipient) {
        MessagesProto.ChatMessage chatMessage = MessagesProto.ChatMessage.newBuilder()
            .setId(String.valueOf(id))
            .setType(MessageType.TEXT)
            .setSender(sender)
            .setRecipient(recipient)
            .setContent(content)
            .setTimestamp(Instant.now().toEpochMilli())
            .build();

        sendMessage(WsMessage.newBuilder().setChatMessage(chatMessage).build());
    }

    public void sendDeleteMessage(String messageId, String senderUsername) {
        MessagesProto.DeleteMessageRequest request = MessagesProto.DeleteMessageRequest.newBuilder()
            .setMessageId(messageId)
            .setSenderUsername(senderUsername)
            .build();
        sendMessage(WsMessage.newBuilder().setDeleteMessageRequest(request).build());
    }

    public void sendClearHistory(String sender, String recipient) {
        MessagesProto.ClearHistoryRequest request = MessagesProto.ClearHistoryRequest.newBuilder()
            .setSender(sender)
            .setRecipient(recipient)
            .build();
        sendMessage(WsMessage.newBuilder().setClearHistoryRequest(request).build());
    }

    public void sendEditMessage(ChatMessage message, String newContent, String currentUserId, String recipient) {
        MessagesProto.ChatMessage editedMessage = MessagesProto.ChatMessage.newBuilder()
            .setId(String.valueOf(message.getId()))
            .setType(MessageType.TEXT)
            .setSender(currentUserId)
            .setRecipient(recipient)
            .setContent(newContent)
            .setTimestamp(Instant.now().toEpochMilli())
            .build();
        sendMessage(WsMessage.newBuilder().setChatMessage(editedMessage).build());
    }

    public void sendMarkAsRead(String sender, String recipient, List<Long> messageIds) {
        MessagesProto.MarkMessagesAsReadRequest.Builder builder = MessagesProto.MarkMessagesAsReadRequest.newBuilder()
            .setSender(sender)
            .setRecipient(recipient);
        
        for (Long id : messageIds) {
            builder.addMessageIds(String.valueOf(id));
        }
        sendMessage(WsMessage.newBuilder().setMarkMessagesAsReadRequest(builder.build()).build());
    }

    private void sendMessage(WsMessage message) {
        if (webSocketService.isConnected()) {
            webSocketService.sendMessage(message);
        }
    }
}