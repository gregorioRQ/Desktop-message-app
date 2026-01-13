package com.pola.service;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;
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
    private String currentUsername;
    private ContactService contactService;
    private Consumer<String> errorListener;
    private final Map<WsMessage.PayloadCase, Consumer<WsMessage>> messageHandlers = new HashMap<>();
    
    public MessageService(WebSocketService webSocketService, ContactService contactService) {
        this.webSocketService = webSocketService;
        this.messageRepository = new MessageRepository();
        this.currentChatMessages = FXCollections.observableArrayList();
        this.notifications = FXCollections.observableArrayList();
        this.contactService = contactService;
        initializeHandlers();
    }

    // Establece el usuario actual
    public void setCurrentUserId(String userId){
        this.currentUserId = userId;
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    private void initializeHandlers() {
        messageHandlers.put(WsMessage.PayloadCase.CHAT_MESSAGE_RESPONSE, this::handleMessageError);
        messageHandlers.put(WsMessage.PayloadCase.UNREAD_MESSAGES_LIST, msg -> processUnreadMessages(msg.getUnreadMessagesList()));
        messageHandlers.put(WsMessage.PayloadCase.DELETE_MESSAGE_REQUEST, msg -> processDeleteMessageRequest(msg.getDeleteMessageRequest()));
        messageHandlers.put(WsMessage.PayloadCase.CLEAR_HISTORY_REQUEST, msg -> processClearHistoryRequest(msg.getClearHistoryRequest()));
        messageHandlers.put(WsMessage.PayloadCase.CHAT_MESSAGE, this::handleChatMessage);
        messageHandlers.put(WsMessage.PayloadCase.UNBLOCKED_USERS_LIST, msg -> processUnblockedUsersList(msg.getUnblockedUsersList()));
        messageHandlers.put(WsMessage.PayloadCase.BLOCKED_USERS_LIST, msg -> processBlockedUsersList(msg.getBlockedUsersList()));
    }

    // Establece un listener para errores recibidos del servidor
    public void setErrorListener(Consumer<String> listener) {
        this.errorListener = listener;
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
        WsMessage.PayloadCase payloadCase = wsMessage.getPayloadCase();
        Consumer<WsMessage> handler = messageHandlers.get(payloadCase);
        
        if (handler != null) {
            handler.accept(wsMessage);
        } else {
            System.out.println("Tipo de mensaje no manejado o ignorado: " + payloadCase);
        }
    }

    private void handleMessageError(WsMessage wsMessage) {
        com.pola.proto.MessagesProto.ChatMessageResponse response = wsMessage.getChatMessageResponse();
        String errorContent = response.getErrorMessage();
        System.out.println("Error recibido del servidor: " + errorContent);
        
        if (response.getCause() == com.pola.proto.MessagesProto.FailureCause.BLOCKED) {
            String recipient = response.getRecipient();
            
            // 1. Marcar persistentemente que este usuario nos bloqueó
            contactService.markUserAsBlockingMe(recipient);

            // 2. Eliminar el mensaje fallido de la DB local y de la UI (Rollback)
            try {
                if (response.getMessageId() != null && !response.getMessageId().isEmpty()) {
                    long msgId = Long.parseLong(response.getMessageId());
                    messageRepository.delete(msgId);
                    Platform.runLater(() -> currentChatMessages.removeIf(m -> m.getId() == msgId));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 3. Mostrar mensaje de advertencia del sistema si estamos en el chat
            if (currentContact != null && currentContact.getContactUsername().equals(recipient)) {
                Platform.runLater(() -> {
                    ChatMessage systemMessage = new ChatMessage(recipient, errorContent, "Sistema");
                    systemMessage.setId(System.currentTimeMillis());
                    currentChatMessages.add(systemMessage);
                });
            }
        } else if (errorListener != null) {
            Platform.runLater(() -> errorListener.accept(errorContent));
        }
    }

    private void handleChatMessage(WsMessage wsMessage) {
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

    /**
     * Vacía el historial de mensajes con un contacto.
     * @param contact El contacto del chat.
     * @param deleteForEveryone Si es true, envía petición al servidor para borrar mensajes.
     */
    public void clearChatHistory(Contact contact, boolean deleteForEveryone) {
        try {
            // Si es "para todos", enviar peticiones al servidor
            // NOTA: Idealmente esto debería ser una sola petición 'ClearChatRequest' en el protocolo
            if (deleteForEveryone && webSocketService.isConnected()) {
                sendClearHistoryToServer(contact);
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

    private void sendClearHistoryToServer(Contact contact){
        try {
            // Construir la solicitud de vaciado de historial
            com.pola.proto.MessagesProto.ClearHistoryRequest clearRequest = 
                com.pola.proto.MessagesProto.ClearHistoryRequest.newBuilder()
                    .setSender(currentUsername)
                    .setRecipient(contact.getContactUsername())
                    .build();
            
            WsMessage wsMessage = WsMessage.newBuilder()
                .setClearHistoryRequest(clearRequest)
                .build();
            
            webSocketService.sendMessage(wsMessage);
            System.out.println("Solicitud de vaciar historial enviada para: " + contact.getContactUsername());
            
        } catch (Exception e) {
            System.err.println("Error al enviar solicitud de vaciar historial: " + e.getMessage());
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
     * Método auxiliar para procesar la solicitud de eliminación de un mensaje
     */
    private void processDeleteMessageRequest(com.pola.proto.MessagesProto.DeleteMessageRequest request) {
        try {
            long messageId = Long.parseLong(request.getMessageId());
            
            // Eliminar de la base de datos local
            messageRepository.delete(messageId);
            
            // Actualizar la UI eliminando el mensaje de la lista observable
            Platform.runLater(() -> {
                currentChatMessages.removeIf(m -> m.getId() == messageId);
            });
            
            System.out.println("Mensaje eliminado por solicitud remota: " + messageId);
            
        } catch (Exception e) {
            System.err.println("Error al procesar eliminación de mensaje (no crítico): " + e.getMessage());
        }
    }

    /**
     * Procesa una solicitud de vaciado de historial recibida del servidor
     */
    private void processClearHistoryRequest(com.pola.proto.MessagesProto.ClearHistoryRequest request) {
        try {
            String senderUsername = request.getSender();
            System.out.println(senderUsername);
            
            // Eliminar mensajes locales de ese contacto
            messageRepository.deleteByContactUsername(senderUsername);
            
            // Si estamos viendo ese chat, limpiar la UI
            if (currentContact != null && currentContact.getContactUsername().equals(senderUsername)) {
                Platform.runLater(() -> currentChatMessages.clear());
            }
            System.out.println("Historial vaciado por solicitud remota de: " + senderUsername);
        } catch (SQLException e) {
            System.err.println("Error al procesar vaciado de historial remoto: " + e.getMessage());
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

    /**
     * Procesa la lista de usuarios que han bloqueado al cliente
     */
    private void processBlockedUsersList(com.pola.proto.MessagesProto.BlockedUsersList list) {
        for (String username : list.getUsersList()) {
            // 1. Actualizar estado en ContactService (DB y Memoria)
            contactService.markUserAsBlockingMe(username);
            
            // 2. Crear mensaje del sistema para informar
            String content = "Este usuario te ha bloqueado.";
            try {
                ChatMessage localMessage = new ChatMessage(username, content, "Sistema");
                localMessage.setId(System.currentTimeMillis());
                
                // Guardar mensaje del sistema en historial local
                messageRepository.create(localMessage);
                
                // 3. Actualizar UI o Notificaciones
                if (currentContact != null && currentContact.getContactUsername().equals(username)) {
                    Platform.runLater(() -> currentChatMessages.add(localMessage));
                } else {
                    updateNotification(username);
                }
            } catch (Exception e) {
                System.err.println("Error procesando bloqueo de usuario: " + username);
                e.printStackTrace();
            }
        }
    }

    /**
     * Procesa la lista de usuarios que han desbloqueado al cliente mientras estaba offline
     */
    private void processUnblockedUsersList(com.pola.proto.MessagesProto.UnblockedUsersList list) {
        for (String username : list.getUsersList()) {
            // 1. Actualizar estado en ContactService (DB y Memoria)
            contactService.markUserAsUnblockingMe(username);
            
            // 2. Crear mensaje del sistema para informar
            String content = "Este usuario te ha desbloqueado.";
            try {
                ChatMessage localMessage = new ChatMessage(username, content, "Sistema");
                localMessage.setId(System.currentTimeMillis());
                
                // Guardar mensaje del sistema en historial local
                messageRepository.create(localMessage);
                
                // 3. Actualizar UI o Notificaciones
                if (currentContact != null && currentContact.getContactUsername().equals(username)) {
                    Platform.runLater(() -> currentChatMessages.add(localMessage));
                } else {
                    updateNotification(username);
                }
            } catch (Exception e) {
                System.err.println("Error procesando desbloqueo de usuario: " + username);
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
