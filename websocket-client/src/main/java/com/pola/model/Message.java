package com.pola.model;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Message {
    private final StringProperty sender;
    private final StringProperty content;
    private final ObjectProperty<Instant> timestamp;
    
    private static final DateTimeFormatter FORMATTER = 
            DateTimeFormatter.ofPattern("HH:mm:ss");
    
    public Message(String sender, String content, Instant timestamp) {
        this.sender = new SimpleStringProperty(sender);
        this.content = new SimpleStringProperty(content);
        this.timestamp = new SimpleObjectProperty<>(timestamp);
    }
    
    // Getters
    public String getSender() {
        return sender.get();
    }
    
    public StringProperty senderProperty() {
        return sender;
    }
    
    public String getContent() {
        return content.get();
    }
    
    public StringProperty contentProperty() {
        return content;
    }
    
    public Instant getTimestamp() {
        return timestamp.get();
    }
    
    public ObjectProperty<Instant> timestampProperty() {
        return timestamp;
    }
    
    /**
     * Formato de tiempo legible
     */
    public String getFormattedTime() {
        LocalDateTime dateTime = LocalDateTime.ofInstant(timestamp.get(), 
                ZoneId.systemDefault());
        return dateTime.format(FORMATTER);
    }
    
    /**
     * Representación del mensaje para la UI
     */
    public String getDisplayText() {
        return String.format("[%s] %s: %s", 
                getFormattedTime(), getSender(), getContent());
    }
}
