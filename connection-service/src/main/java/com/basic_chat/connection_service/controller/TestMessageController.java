package com.basic_chat.connection_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.connection_service.service.SessionRegistryService;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.ChatMessage;
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
}