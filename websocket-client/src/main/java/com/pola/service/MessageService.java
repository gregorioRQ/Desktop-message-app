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
            
            List<ChatMessage> messages = messageRepository.findByContactUsername(contact.getContactUsername());
            currentChatMessages.setAll(messages);

            // marcar mensajes como leidos
            messageRepository.markAllAsReadByContactUsername(contact.getContactUsername());
            System.out.println("Historial cargado: " + messages.size() + " mensajes");
        }catch(SQLException e){
            e.printStackTrace();
        }
    }

    /**
     * Envía un mensaje de texto
     */
    public void sendTextMessage(String content, String username) {
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
            ChatMessage localMessage = new ChatMessage(currentContact.getContactUsername(), content, username);
            System.out.println("valor de currentUserId: " + currentUserId);
            ChatMessage saved = messageRepository.create(localMessage);

            // mostrar en la UI
            currentChatMessages.add(saved);

            // enviar por websocket
            com.pola.proto.MessagesProto.ChatMessage chatMessage = com.pola.proto.MessagesProto.ChatMessage.newBuilder()
                .setId(UUID.randomUUID().toString())
                .setType(MessageType.TEXT)
                .setSender(username)
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
      
        System.out.println("Iniciando proceso de guardado de mensaje");
        
        try {
            // buscar si el contacto existe o crear uno nuevo
            Contact contact = this.contactService.findContactByUsername(currentUserId, senderId).orElseGet(()->{
                // Contacto nuevo = agregarlo
                String senderUsername = protobufMessage.getSender();
                return this.contactService.addContact(currentUserId, senderUsername);
            });

            if(contact == null){
                System.err.println("No se pudo obtener o crear el contacto");
                return;
            }

            // crear el mensaje local
            ChatMessage localMessage = new ChatMessage(contact.getContactUsername(), content, senderId);

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

    // eliminar un mensaje del local como del servidor
    public void deleteOneMessage(ChatMessage message){
        try {
            messageRepository.delete(message.getId());
            currentChatMessages.remove(message);

            //Notificar al servidor para eliminar
            if(webSocketService.isConnected()){
                sendDeleteMessageToServer(message);
            }
        } catch (SQLException ex) {
            System.err.println("Error eliminando el mensaje");
            ex.printStackTrace();
        }
    }

    private void sendDeleteMessageToServer(ChatMessage message){
        try {
            
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    public void editMessage(ChatMessage message, String newContent){
        try {
            messageRepository.updateContent(message.getId(), newContent);
            message.setContent(newContent);
            if(webSocketService.isConnected()){
                sendDeleteMessageToServer(message, newContent);
            }
        } catch (SQLException e) {
            System.err.println("Error editanto el mensaje");
            e.printStackTrace();
        }
    }

    private void sendDeleteMessageToServer(ChatMessage message, String newContent){
        
    }
}
