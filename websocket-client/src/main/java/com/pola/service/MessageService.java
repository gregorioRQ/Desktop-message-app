package com.pola.service;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.pola.model.ChatMessage;
import com.pola.model.Contact;
import com.pola.model.Notification;

import com.pola.proto.MessagesProto.MessageType;
import com.pola.proto.MessagesProto.WsMessage;
import com.pola.repository.MessageRepository;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Servicio para gestionar mensajes
 * Principio SOLID: Single Responsibility - Solo maneja la lógica de mensajes
 */
public class MessageService {
    private final ObservableList<ChatMessage> currentChatMessages;
    private final ObservableList<Notification> notifications;
    private final MessageRepository messageRepository;
    private final WebSocketService webSocketService;
    private Contact currentContact;
    private String currentUserId;
    private ContactService contactService;
    
    public MessageService(WebSocketService webSocketService, ContactService contactService) {
        this.webSocketService = webSocketService;
        this.messageRepository = new MessageRepository();
        this.currentChatMessages = FXCollections.observableArrayList();
        this.notifications = FXCollections.observableArrayList();
        this.contactService = contactService;
    }

    // Establece el usuario actual
    public void setCurrentUserId(String userId){
        this.currentUserId = userId;
    }

    // Carga el historial de mensajes de un contacto
    public void loadChatHistory(Contact contact){
        this.currentContact = contact;
        
        // Limpiar notificaciones de este contacto al entrar al chat
        removeNotification(contact.getContactUsername());

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
            // generar un id aleatorio para el mensaje local y del servidor
            long id = Math.abs(UUID.randomUUID().getLeastSignificantBits());
            localMessage.setId(id);
            
            ChatMessage saved = messageRepository.create(localMessage);

            // mostrar en la UI
            currentChatMessages.add(saved);

            // enviar por websocket
            com.pola.proto.MessagesProto.ChatMessage chatMessage = com.pola.proto.MessagesProto.ChatMessage.newBuilder()
                .setId(String.valueOf(id))
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

            System.out.println("Mensaje enviado a: " + currentContact.getContactUsername() + "id: " + localMessage.getId());

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Procesa un mensaje recibido
     */
    public void processReceivedMessage(WsMessage wsMessage) {
        if(wsMessage.hasUnreadMessagesList()){
            processUnreadMessages(wsMessage.getUnreadMessagesList());
            return;
        }

        if(!wsMessage.hasChatMessage()){
            return;
        }

        com.pola.proto.MessagesProto.ChatMessage protobufMessage = wsMessage.getChatMessage();
        String senderId = protobufMessage.getSender();
        String content = protobufMessage.getContent();
        long messageId = Long.parseLong(protobufMessage.getId());
      
        System.out.println("Iniciando proceso de guardado de mensaje");
        
        try {
            // Validar si el mensaje ya existe para evitar duplicados
            if (messageRepository.existsById(messageId)) {
                System.out.println("Mensaje recibido ya existe en local. ID: " + messageId);
                return;
            }

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
            localMessage.setId(messageId); // Asignar el ID del servidor

            // guardar en la db
            ChatMessage saved = messageRepository.create(localMessage);

            // si es del contacto actualmente seleccionado mostrarlo en la UI
            if(currentContact != null && currentContact.getId() == contact.getId()){
                Platform.runLater(() -> currentChatMessages.add(saved));
                messageRepository.markAsRead(saved.getId());
            } else {
                updateNotification(senderId);
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
            // Crear la solicitud de eliminación con protobuf
            com.pola.proto.MessagesProto.DeleteMessageRequest deleteRequest = 
                com.pola.proto.MessagesProto.DeleteMessageRequest.newBuilder()
                    .setMessageId(String.valueOf(message.getId()))
                    .setSenderUsername(currentUserId)
                    .build();
            
            // Enviar como WsMessage
            WsMessage wsMessage = WsMessage.newBuilder()
                .setDeleteMessageRequest(deleteRequest)
                .build();
            
            webSocketService.sendMessage(wsMessage);
            System.out.println("Solicitud de eliminación de mensaje enviada: " + message.getId());
            
        } catch (Exception e) {
            System.err.println("Error al enviar solicitud de eliminación: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void editMessage(ChatMessage message, String newContent){
        try {
            messageRepository.updateContent(message.getId(), newContent);
            message.setContent(newContent);
            if(webSocketService.isConnected()){
                sendEditMessageToServer(message, newContent);
            }
        } catch (SQLException e) {
            System.err.println("Error editanto el mensaje");
            e.printStackTrace();
        }
    }

    private void sendEditMessageToServer(ChatMessage message, String newContent){
        try {
            // Crear un nuevo mensaje de chat con el contenido actualizado
            com.pola.proto.MessagesProto.ChatMessage editedMessage = 
                com.pola.proto.MessagesProto.ChatMessage.newBuilder()
                    .setId(String.valueOf(message.getId()))
                    .setType(MessageType.TEXT)
                    .setSender(currentUserId)
                    .setRecipient(currentContact != null ? currentContact.getContactUsername() : "")
                    .setContent(newContent)
                    .setTimestamp(Instant.now().toEpochMilli())
                    .build();
            
            // Enviar como WsMessage
            WsMessage wsMessage = WsMessage.newBuilder()
                .setChatMessage(editedMessage)
                .build();
            
            webSocketService.sendMessage(wsMessage);
            System.out.println("Solicitud de edición de mensaje enviada: " + message.getId());
            
        } catch (Exception e) {
            System.err.println("Error al enviar solicitud de edición: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Procesa la lista de mensajes no leídos recibida del servidor
     */
    private void processUnreadMessages(com.pola.proto.MessagesProto.UnreadMessagesList unreadMessagesList) {
        List<com.pola.proto.MessagesProto.ChatMessage> messages = unreadMessagesList.getMessagesList();
        System.out.println("Procesando lista de mensajes no leídos: " + messages.size());

        for (com.pola.proto.MessagesProto.ChatMessage protoMessage : messages) {
            String senderUsername = protoMessage.getSender();
            String content = protoMessage.getContent();
            long messageId = Long.parseLong(protoMessage.getId());

            try {
                // Buscar o crear contacto
                Contact contact = this.contactService.findContactByUsername(currentUserId, senderUsername)
                        .orElseGet(() -> this.contactService.addContact(currentUserId, senderUsername));

                if (contact == null) {
                    System.err.println("No se pudo obtener contacto para mensaje de: " + senderUsername);
                    continue;
                }

                // Validar si el mensaje ya existe
                if (messageRepository.existsById(messageId)) {
                    System.out.println("Mensaje no leído ya existe en local (ignorado). ID: " + messageId);
                    continue;
                }

                // Crear mensaje local
                ChatMessage localMessage = new ChatMessage(contact.getContactUsername(), content, senderUsername);
                localMessage.setId(messageId);
                
                // Guardar en DB (se guarda como no leído por defecto)
                ChatMessage saved = messageRepository.create(localMessage);

                // Si estamos en el chat de este contacto, mostrar y marcar como leído
                if (currentContact != null && currentContact.getId() == contact.getId()) {
                    Platform.runLater(() -> currentChatMessages.add(saved));
                    messageRepository.markAsRead(saved.getId());
                } else {
                    updateNotification(senderUsername);
                }
            } catch (SQLException e) {
                System.err.println("Error procesando mensaje no leído: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void updateNotification(String senderUsername) {
        Platform.runLater(() -> {
            Optional<Notification> existing = notifications.stream()
                .filter(n -> n.getSenderUsername().equals(senderUsername))
                .findFirst();
            
            if (existing.isPresent()) {
                Notification n = existing.get();
                n.incrementCount();
                // Forzar actualización en la lista (reemplazando el elemento)
                int idx = notifications.indexOf(n);
                notifications.set(idx, n);
            } else {
                notifications.add(new Notification(senderUsername, 1));
            }
        });
    }

    private void removeNotification(String senderUsername) {
        Platform.runLater(() -> {
            notifications.removeIf(n -> n.getSenderUsername().equals(senderUsername));
        });
    }

    public ObservableList<Notification> getNotifications() {
        return notifications;
    }
}
