package com.basic_chat.connection_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.basic_chat.connection_service.service.ConnectionMessageDispatcher;
import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.connection_service.service.SessionRegistryService;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.ChatMessage;
import com.basic_chat.proto.MessagesProto.ImageMessage;
import com.basic_chat.proto.MessagesProto.MessageStatus;
import com.basic_chat.proto.MessagesProto.MessageType;
import com.basic_chat.proto.MessagesProto.WsMessage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Slf4j
public class TestMessageController {

    private final MessageRouterService messageRouterService;
    private final SessionRegistryService sessionRegistryService;
    private final ConnectionMessageDispatcher messageDispatcher;

    @PostMapping("/send-message")
    public ResponseEntity<SendMessageResponse> sendMessage(@RequestBody SendMessageRequest request) {
        log.info("Recibida solicitud de prueba - sender: {}, recipient: {}", request.getSender(), request.getRecipient());

        long timestamp = System.currentTimeMillis();

        ChatMessage chatMessage = ChatMessage.newBuilder()
                .setId(String.valueOf(timestamp))
                .setType(MessageType.TEXT)
                .setSender(request.getSender())
                .setRecipient(request.getRecipient())
                .setContent(request.getContent())
                .setTimestamp(timestamp)
                .build();

        WsMessage wsMessage = WsMessage.newBuilder()
                .setChatMessage(chatMessage)
                .build();

        byte[] messageData = wsMessage.toByteArray();

        messageRouterService.routeMessage(request.getSender(), request.getRecipient(), messageData);

        return ResponseEntity.ok(new SendMessageResponse(true, "Mensaje enrutado a " + request.getRecipient()));
    }

    @PostMapping("/send-image")
    public ResponseEntity<SendImageResponse> sendImage(@RequestBody SendImageRequest request) {
        log.info("Recibida solicitud de prueba de imagen - sender: {}, receiverId: {}", 
            request.getSender(), request.getReceiverId());

        long timestamp = System.currentTimeMillis();

        ImageMessage imageMessage = ImageMessage.newBuilder()
                .setMediaId(request.getMediaId())
                .setSenderId(request.getSender())
                .setReceiverId(request.getReceiverId())
                .setFullImageUrl(request.getFullImageUrl())
                .setFileSize(request.getFileSize())
                .setTimestamp(timestamp)
                .setStatus(MessageStatus.SENT)
                .build();

        WsMessage wsMessage = WsMessage.newBuilder()
                .setImageMessage(imageMessage)
                .build();

        messageDispatcher.dispatch(request.getSender(), wsMessage);

        return ResponseEntity.ok(new SendImageResponse(true, "Mensaje de imagen enrutado a " + request.getReceiverId()));
    }

    public static class SendMessageRequest {
        private String sender;
        private String recipient;
        private String content;

        public String getSender() { return sender; }
        public void setSender(String sender) { this.sender = sender; }
        public String getRecipient() { return recipient; }
        public void setRecipient(String recipient) { this.recipient = recipient; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    public static class SendMessageResponse {
        private boolean success;
        private String message;

        public SendMessageResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }

    public static class SendImageRequest {
        private String sender;
        private String receiverId;
        private String mediaId;
        private String fullImageUrl;
        private long fileSize;

        public String getSender() { return sender; }
        public void setSender(String sender) { this.sender = sender; }
        public String getReceiverId() { return receiverId; }
        public void setReceiverId(String receiverId) { this.receiverId = receiverId; }
        public String getMediaId() { return mediaId; }
        public void setMediaId(String mediaId) { this.mediaId = mediaId; }
        public String getFullImageUrl() { return fullImageUrl; }
        public void setFullImageUrl(String fullImageUrl) { this.fullImageUrl = fullImageUrl; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    }

    public static class SendImageResponse {
        private boolean success;
        private String message;

        public SendImageResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}