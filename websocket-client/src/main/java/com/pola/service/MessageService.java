package com.pola.service;

import java.sql.SQLException;
import java.util.List;
import java.util.function.Consumer;
import java.util.UUID;

import com.pola.model.ChatMessage;
import com.pola.model.Contact;
import com.pola.model.Notification;
import com.pola.proto.MessagesProto.WsMessage;
import com.pola.repository.MessageRepository;
import com.pola.util.MessageProcessingContext;

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
    private final MessageSender messageSender;
    private final IncomingMessageProcessor messageProcessor;
    private Contact currentContact;
    private String currentUserId;
    private String currentUsername;
    private ContactService contactService;
    
    public MessageService(WebSocketService webSocketService, ContactService contactService) {
        this(webSocketService, contactService, new MessageRepository());
    }

    public MessageService(WebSocketService webSocketService, ContactService contactService, MessageRepository messageRepository) {
        this.webSocketService = webSocketService;
        this.contactService = contactService;
        this.messageRepository = messageRepository;
        this.currentChatMessages = FXCollections.observableArrayList();
        this.notifications = FXCollections.observableArrayList();
        
        this.messageSender = new MessageSender(webSocketService);

        MessageProcessingContext context = new MessageProcessingContext(
                messageRepository,
                contactService,
                messageSender,
                currentChatMessages,
                notifications,
                () -> currentContact,
                () -> currentUserId,
                () -> currentUsername
        );

        this.messageProcessor = new IncomingMessageProcessor(context);
    }

    // Establece el usuario actual
    public void setCurrentUserId(String userId){
        this.currentUserId = userId;
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    // Establece un listener para errores recibidos del servidor
    public void setErrorListener(Consumer<String> listener) {
        messageProcessor.setErrorListener(listener);
    }

    // Carga el historial de mensajes de un contacto
    public void loadChatHistory(Contact contact){
        this.currentContact = contact;
        
        // Limpiar notificaciones de este contacto al entrar al chat
        removeNotification(contact.getContactUsername());

        try{
            
            List<ChatMessage> messages = messageRepository.findByContactUsername(contact.getContactUsername());
            currentChatMessages.setAll(messages);

            // 1. Obtener solo los IDs que REALMENTE están sin leer
            List<Long> unreadIds = messageRepository.getUnreadMessageIds(contact.getContactUsername());
            
            if (!unreadIds.isEmpty()) {
                // 2. Actualizar DB local (Batch)
                messageRepository.markMultipleAsRead(unreadIds);
                
                // 3. Enviar al servidor SOLO los IDs nuevos
                if (webSocketService.isConnected()) {
                    messageSender.sendMarkAsRead(currentUsername, contact.getContactUsername(), unreadIds);
                }
                
              
                messages.forEach(m -> { if(unreadIds.contains(m.getId())) m.setRead(true); });
            }
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

        // Verificar si estamos bloqueados por este usuario antes de intentar nada
        if (contactService.isUserBlockingMe(currentContact.getContactUsername())) {
            System.out.println("Intento de envío bloqueado: El destinatario te ha bloqueado.");
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
            messageSender.sendTextMessage(id, content, username, currentContact.getContactUsername());

            System.out.println("Mensaje enviado a: " + currentContact.getContactUsername() + "id: " + localMessage.getId());

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Procesa un mensaje recibido
     */
    public void processReceivedMessage(WsMessage wsMessage) {
        messageProcessor.process(wsMessage);
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

    /**
     * Vacía el historial de mensajes con un contacto.
     * @param contact El contacto del chat.
     * @param deleteForEveryone Si es true, envía petición al servidor para borrar mensajes.
     */
    public void clearChatHistory(Contact contact, boolean deleteForEveryone) {
        try {
            // Si es "para todos", enviar peticiones al servidor
            if (deleteForEveryone && webSocketService.isConnected()) {
                messageSender.sendClearHistory(currentUsername, contact.getContactUsername());
            }

            // Eliminar de la base de datos local (siempre se hace en ambos casos)
            messageRepository.deleteByContactUsername(contact.getContactUsername());
            
            // Limpiar la UI si estamos viendo ese chat actualmente
            if (currentContact != null && currentContact.getId() == contact.getId()) {
                Platform.runLater(() -> currentChatMessages.clear());
            }
            
            System.out.println("Historial eliminado (" + (deleteForEveryone ? "global" : "local") + ") para: " + contact.getContactUsername());
            
        } catch (SQLException e) {
            System.err.println("Error al vaciar historial: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // eliminar un mensaje del local como del servidor
    public void deleteOneMessage(ChatMessage message){
        try {
            messageRepository.delete(message.getId());
            currentChatMessages.remove(message);

            //Notificar al servidor para eliminar
            if(webSocketService.isConnected()){
                messageSender.sendDeleteMessage(String.valueOf(message.getId()), currentUserId);
            }
        } catch (SQLException ex) {
            System.err.println("Error eliminando el mensaje");
            ex.printStackTrace();
        }
    }

    public void editMessage(ChatMessage message, String newContent){
        try {
            messageRepository.updateContent(message.getId(), newContent);
            message.setContent(newContent);
            if(webSocketService.isConnected()){
                messageSender.sendEditMessage(message, newContent, currentUserId, 
                    currentContact != null ? currentContact.getContactUsername() : "");
            }
        } catch (SQLException e) {
            System.err.println("Error editanto el mensaje");
            e.printStackTrace();
        }
    }


    private void removeNotification(String senderUsername) {
        Platform.runLater(() -> {
            notifications.removeIf(n -> n.getSenderUsername().equals(senderUsername));
        });
    }

    /**
     * Elimina todas las notificaciones de la bandeja.
     */
    public void clearAllNotifications() {
        Platform.runLater(() -> notifications.clear());
    }

    public ObservableList<Notification> getNotifications() {
        return notifications;
    }
}
