package com.pola.service;

import java.time.Instant;
import java.util.UUID;

import com.pola.model.Message;
import com.pola.proto.MessagesProto.ChatMessage;
import com.pola.proto.MessagesProto.MessageType;
import com.pola.proto.MessagesProto.WsMessage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Servicio para gestionar mensajes
 * Principio SOLID: Single Responsibility - Solo maneja la lógica de mensajes
 */
public class MessageService {
    private final ObservableList<Message> messages;
    private final WebSocketService webSocketService;
    
    public MessageService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
        this.messages = FXCollections.observableArrayList();
    }

    /**
     * Envía un mensaje de texto
     */
    public void sendTextMessage(String content, String sender, String recipient) {
        ChatMessage chatMessage = ChatMessage.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setType(MessageType.CHAT)
                .setSender(sender)
                .setRecipient(recipient)
                .setContent(content)
                .setTimestamp(Instant.now().toEpochMilli())
                .build();
        
        WsMessage wsMessage = WsMessage.newBuilder()
                .setChatMessage(chatMessage)
                .build();
        
        webSocketService.sendMessage(wsMessage);
        
        // Agregar a la lista local
        messages.add(new Message(sender, content, Instant.now()));
    }

    /**
     * Procesa un mensaje recibido
     */
    public void processReceivedMessage(WsMessage wsMessage) {
        if (wsMessage.hasChatMessage()) {
            ChatMessage chatMessage = wsMessage.getChatMessage();
            Message message = new Message(
                    chatMessage.getSender(),
                    chatMessage.getContent(),
                    Instant.ofEpochMilli(chatMessage.getTimestamp())
            );
            messages.add(message);
        }
    }
    
    /**
     * Obtiene la lista observable de mensajes
     */
    public ObservableList<Message> getMessages() {
        return messages;
    }
    
    /**
     * Limpia todos los mensajes
     */
    public void clearMessages() {
        messages.clear();
    }
}
