package com.pola.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ChatMessage {
    private final IntegerProperty id;
    private final StringProperty contactUsername;
    private final StringProperty content;
    private final StringProperty senderId;
    private final ObjectProperty<LocalDateTime> timestamp;
    private final BooleanProperty read;
    
    private static final DateTimeFormatter TIME_FORMATTER = 
            DateTimeFormatter.ofPattern("HH:mm");
    
    public ChatMessage(int id, String contactUsername, String content, String senderId,
                      LocalDateTime timestamp, boolean read) {
        this.id = new SimpleIntegerProperty(id);
        this.contactUsername = new SimpleStringProperty(contactUsername);
        this.content = new SimpleStringProperty(content);
        this.senderId = new SimpleStringProperty(senderId);
        this.timestamp = new SimpleObjectProperty<>(timestamp);
        this.read = new SimpleBooleanProperty(read);
    }
    
    // Constructor simplificado para nuevos mensajes
    public ChatMessage(String contactUsername, String content, String senderId) {
        this(0, contactUsername, content, senderId, LocalDateTime.now(), false);
    }
    
    // Getters y setters
    public int getId() {
        return id.get();
    }
    
    public IntegerProperty idProperty() {
        return id;
    }
    
    public void setId(int id) {
        this.id.set(id);
    }
    
    public String getContactUsername() {
        return contactUsername.get();
    }
    
    public StringProperty contactUsernameProperty() {
        return contactUsername;
    }
    
    public String getContent() {
        return content.get();
    }
    
    public StringProperty contentProperty() {
        return content;
    }
    
    public String getSenderId() {
        return senderId.get();
    }
    
    public StringProperty senderIdProperty() {
        return senderId;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp.get();
    }
    
    public ObjectProperty<LocalDateTime> timestampProperty() {
        return timestamp;
    }
    
    public boolean isRead() {
        return read.get();
    }
    
    public BooleanProperty readProperty() {
        return read;
    }
    
    public void setRead(boolean read) {
        this.read.set(read);
    }

    public void setContent(String content){
        this.content.set(content);
    }
    
    /**
     * Formato de tiempo legible (HH:mm)
     */
    public String getFormattedTime() {
        return timestamp.get().format(TIME_FORMATTER);
    }
    
    /**
     * Texto de visualización para la UI
     * Ejemplo: "[10:30] Juan: Hola"
     */
    public String getDisplayText(String currentUserId) {
        String sender = senderId.get().equals(currentUserId) ? "Tú" : "Contacto";
        return String.format("[%s] %s: %s", getFormattedTime(), sender, content.get());
    }
    
    @Override
    public String toString() {
        return getDisplayText("");
    }
}
