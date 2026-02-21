package com.pola.controller;

import com.pola.model.ChatMessage;
import com.pola.model.ImageProcessingResult;
import com.pola.proto.UploadImageResponse;
import com.pola.service.ContactService;
import com.pola.service.MessageService;
import com.pola.service.WebSocketService;
import com.pola.view.ChatDialogs;

import javafx.scene.paint.Color;

public class MessageActionHelper {
    private final MessageService messageService;
    private final WebSocketService webSocketService;
    private final ContactService contactService;
    private final ChatController chatController;

    public MessageActionHelper(MessageService messageService, WebSocketService webSocketService, 
                               ContactService contactService, ChatController chatController) {
        this.messageService = messageService;
        this.webSocketService = webSocketService;
        this.contactService = contactService;
        this.chatController = chatController;
    }

    public void handleSendMessage() {
        String content = chatController.getMessageInput().getText().trim();
        
        if (content.isEmpty()) {
            return;
        }

        if(chatController.getSelectedContact() == null){
            chatController.showStatus("Selecciona un contacto primero", Color.ORANGE);
            return;
        }
        
        if (!webSocketService.isConnected()) {
            chatController.showStatus("No conectado al servidor", Color.RED);
            return;
        }
        
        try {
            messageService.sendTextMessage(content, chatController.getCurrentUsername());
            chatController.getMessageInput().clear();
        } catch (Exception e) {
            chatController.showStatus("Error al enviar: " + e.getMessage(), Color.RED);
        }
    }

    public void handleDeleteMessage(ChatMessage message) {
        messageService.deleteOneMessage(message);
    }

    public void handleEditMessage(ChatMessage message) {
        ChatDialogs.showEditMessageDialog(message.getContent(), newContent -> {
            messageService.editMessage(message, newContent);
        });
    }

    public void handleClearChat() {
        if (chatController.getSelectedContact() == null) return;

        ChatDialogs.showClearChatDialog(
            chatController.getSelectedContact().getContactUsername(),
            () -> messageService.clearChatHistory(chatController.getSelectedContact(), false),
            () -> messageService.clearChatHistory(chatController.getSelectedContact(), true)
        );
    }

    public void handleSendImage(ImageProcessingResult localResult, UploadImageResponse serverResponse) {
        if (chatController.getSelectedContact() == null) return;
        
        try {
            messageService.sendImageMessage(localResult, serverResponse, chatController.getCurrentUsername());
        } catch (Exception e) {
            chatController.showStatus("Error al enviar imagen: " + e.getMessage(), Color.RED);
        }
    }
}
