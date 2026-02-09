package com.pola.util;

import com.pola.model.ChatMessage;
import com.pola.model.Contact;
import com.pola.model.Notification;
import com.pola.repository.MessageRepository;
import com.pola.service.ContactService;
import com.pola.service.MessageSender;
import javafx.collections.ObservableList;

import java.util.function.Supplier;

/**
 * Objeto de Parámetros para el IncomingMessageProcessor.
 * Agrupa todas las dependencias necesarias para el proceso de manejo de mensajes entrantes.
 */
public class MessageProcessingContext {
    private final MessageRepository messageRepository;
    private final ContactService contactService;
    private final MessageSender messageSender;
    private final ObservableList<ChatMessage> currentChatMessages;
    private final ObservableList<Notification> notifications;
    private final Supplier<Contact> currentContactSupplier;
    private final Supplier<String> currentUserIdSupplier;
    private final Supplier<String> currentUsernameSupplier;

    public MessageProcessingContext(MessageRepository messageRepository,
                                    ContactService contactService,
                                    MessageSender messageSender,
                                    ObservableList<ChatMessage> currentChatMessages,
                                    ObservableList<Notification> notifications,
                                    Supplier<Contact> currentContactSupplier,
                                    Supplier<String> currentUserIdSupplier,
                                    Supplier<String> currentUsernameSupplier) {
        this.messageRepository = messageRepository;
        this.contactService = contactService;
        this.messageSender = messageSender;
        this.currentChatMessages = currentChatMessages;
        this.notifications = notifications;
        this.currentContactSupplier = currentContactSupplier;
        this.currentUserIdSupplier = currentUserIdSupplier;
        this.currentUsernameSupplier = currentUsernameSupplier;
    }

    public MessageRepository getMessageRepository() {
        return messageRepository;
    }

    public ContactService getContactService() {
        return contactService;
    }

    public MessageSender getMessageSender() {
        return messageSender;
    }

    public ObservableList<ChatMessage> getCurrentChatMessages() {
        return currentChatMessages;
    }

    public ObservableList<Notification> getNotifications() {
        return notifications;
    }

    public Supplier<Contact> getCurrentContactSupplier() {
        return currentContactSupplier;
    }

    public Supplier<String> getCurrentUserIdSupplier() {
        return currentUserIdSupplier;
    }

    public Supplier<String> getCurrentUsernameSupplier() {
        return currentUsernameSupplier;
    }
}