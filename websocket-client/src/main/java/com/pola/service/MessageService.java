package com.pola.service;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.pola.model.ChatMessage;
import com.pola.model.Contact;

import com.pola.proto.MessagesProto.MessageType;
import com.pola.proto.MessagesProto.WsMessage;
import com.pola.repository.MessageRepository;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Servicio para gestionar mensajes
 * Principio SOLID: Single Responsibility - Solo maneja la lógica de mensajes
 */
public class MessageService {
    private final ObservableList<ChatMessage> currentChatMessages;
    private final MessageRepository messageRepository;
    private final WebSocketService webSocketService;
    private Contact currentContact;
    private String currentUserId;
    private ContactService contactService;
    
    public MessageService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
        this.messageRepository = new MessageRepository();
        this.currentChatMessages = FXCollections.observableArrayList();
        this.contactService = new ContactService();
    }

    // Establece el usuario actual
    public void setCurrentUserId(String userId){
        this.currentUserId = userId;
    }

    // Carga el historial de mensajes de un contacto
    public void loadChatHistory(Contact contact){
        this.currentContact = contact;

        try{
            List<ChatMessage> messages = messageRepository.findByContactId(contact.getId());
            currentChatMessages.setAll(messages);

            // marcar mensajes como leidos
            messageRepository.markAllAsReadByContactId(contact.getId());
            System.out.println("Historial cargado: " + messages.size() + " mensajes");
        }catch(SQLException e){
            e.printStackTrace();
        }
    }

    /**
     * Envía un mensaje de texto
     */
    public void sendTextMessage(String content) {
        if(currentContact == null){
            System.out.println("No hay contacto seleccionado");
            return;
        }

        if(content == null || content.trim().isEmpty()){
            System.err.println("El mensaje está vacío");
            return;
        }

        try {
            // guardar en la db local
            ChatMessage localMessage = new ChatMessage(currentContact.getId(), content, currentUserId);

            ChatMessage saved = messageRepository.create(localMessage);

            // mostrar en la UI
            currentChatMessages.add(saved);

            // enviar por websocket
            com.pola.proto.MessagesProto.ChatMessage chatMessage = com.pola.proto.MessagesProto.ChatMessage.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setType(MessageType.TEXT)
                .setSender(currentUserId)
                .setRecipient(currentContact.getContactUsername())
                .setContent(content)
                .setTimestamp(Instant.now().toEpochMilli())
                .build();

                WsMessage wsMessage = WsMessage.newBuilder()
                .setChatMessage(chatMessage)
                .build();

            webSocketService.sendMessage(wsMessage);

            System.out.println("Mensaje enviado a: " + currentContact.getContactUsername());

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Procesa un mensaje recibido
     */
    public void processReceivedMessage(WsMessage wsMessage) {
        if(!wsMessage.hasChatMessage()){
            return;
        }

        com.pola.proto.MessagesProto.ChatMessage protobufMessage = wsMessage.getChatMessage();
        String senderId = protobufMessage.getSender();
        String content = protobufMessage.getContent();

        try {
            // buscar si el contacto existe o crear uno nuevo
            Contact contact = this.contactService.findContactByUsername(currentUserId, senderId).orElseGet(()->{
                // Contacto nuevo = agregarlo
                String senderUsername = protobufMessage.getSender();
                return this.contactService.addContact(currentUserId, senderUsername, null);
            });

            if(contact == null){
                System.err.println("No se pudo obtener o crear el contacto");
                return;
            }

            // crear el mensaje local
            ChatMessage localMessage = new ChatMessage(contact.getId(), content, senderId);

            // guardar en la db
            ChatMessage saved = messageRepository.create(localMessage);

            // si es del contacto actualmente seleccionado mostrarlo en la UI
            if(currentContact != null && currentContact.getId() == contact.getId()){
                currentChatMessages.add(saved);
                messageRepository.markAsRead(saved.getId());
            }

        } catch (SQLException e) {
            System.out.println("Error procesando el mensaje recibido");
            e.printStackTrace();
        }
    }
    
    /**
     * Obtiene la lista observable de mensajes
     */
    public ObservableList<ChatMessage> getMessages() {
        return currentChatMessages;
    }
    
    /**
     * Limpia todos los mensajes
     */
    public void clearMessages() {
        currentContact = null;
        currentChatMessages.clear();
    }

    // obtener el contacto actual
    public Contact getCurrenteContact(){
        return currentContact;
    }
}
