package com.pola.service;

import com.google.protobuf.Message;
import com.pola.model.ChatMessage;
import com.pola.proto.ThumbnailMessage;
import com.pola.proto.MessageStatus;
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
    private WebSocketService mediaWebSocketService;

    public MessageSender(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    public void setMediaWebSocketService(WebSocketService mediaWebSocketService) {
        this.mediaWebSocketService = mediaWebSocketService;
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

    public void sendImageMessage(String mediaId, byte[] thumbnailData, String fullImageUrl, 
                               String sender, String recipient, int width, int height, long fileSize) {
        
        ThumbnailMessage thumbnailMsg = ThumbnailMessage.newBuilder()
            .setMediaId(mediaId)
            .setSenderId(sender)
            .setReceiverId(recipient)
            .setThumbnailData(com.google.protobuf.ByteString.copyFrom(thumbnailData))
            .setFullImageUrl(fullImageUrl)
            .setOriginalWidth(width)
            .setOriginalHeight(height)
            .setFileSize(fileSize)
            .setTimestamp(Instant.now().toEpochMilli())
            .setStatus(MessageStatus.SENT)
            .build();

        if (mediaWebSocketService != null && mediaWebSocketService.isConnected()) {
            mediaWebSocketService.sendMessage(thumbnailMsg);
        } else {
            System.err.println("Error: Servicio de media no conectado. No se pudo enviar el thumbnail.");
        }
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

    public void sendContactIdentity(String myUserId, String myUsername, String contactUsername) {
        MessagesProto.ContactIdentity identity = MessagesProto.ContactIdentity.newBuilder()
            .setSenderId(myUserId)
            .setSenderUsername(myUsername)
            .setContactUsername(contactUsername)
            .build();
        
        sendMessage(WsMessage.newBuilder().setContactIdentity(identity).build());
    }

    private void sendMessage(Message message) {
        if (webSocketService.isConnected()) {
            webSocketService.sendMessage(message);
        }
    }
}