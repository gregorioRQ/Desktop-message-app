package com.pola.model;

import java.time.LocalDateTime;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class Contact {
    private final IntegerProperty id;
    private final StringProperty userId;
    private final StringProperty contactUsername;
    private final StringProperty contactUserId;
    private final BooleanProperty blocked;
    private final ObjectProperty<LocalDateTime> createdAt;
    private final ObjectProperty<LocalDateTime> updatedAt;
    
    public Contact(int id, String userId, 
                   String contactUsername, String contactUserId, boolean blocked,
                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = new SimpleIntegerProperty(id);
        this.userId = new SimpleStringProperty(userId);
        this.contactUsername = new SimpleStringProperty(contactUsername);
        this.contactUserId = new SimpleStringProperty(contactUserId);
        this.blocked = new SimpleBooleanProperty(blocked);
        this.createdAt = new SimpleObjectProperty<>(createdAt);
        this.updatedAt = new SimpleObjectProperty<>(updatedAt);
    }
    
    // Constructor simplificado para crear nuevos contactos
    public Contact(String userId, String contactUsername, String contactUserId) {
        this(0, userId, contactUsername, contactUserId, false, 
             LocalDateTime.now(), LocalDateTime.now());
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
    
    public String getUserId() {
        return userId.get();
    }
    
    public StringProperty userIdProperty() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId.set(userId);
    }
    
    public String getContactUsername() {
        return contactUsername.get();
    }
    
    public StringProperty contactUsernameProperty() {
        return contactUsername;
    }
    
    public void setContactUsername(String contactUsername) {
        this.contactUsername.set(contactUsername);
    }
    
    public String getContactUserId() {
        return contactUserId.get();
    }

    public StringProperty contactUserIdProperty() {
        return contactUserId;
    }

    public void setContactUserId(String contactUserId) {
        this.contactUserId.set(contactUserId);
    }

    public boolean isBlocked() {
        return blocked.get();
    }
    
    public BooleanProperty blockedProperty() {
        return blocked;
    }
    
    public void setBlocked(boolean blocked) {
        this.blocked.set(blocked);
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt.get();
    }
    
    public ObjectProperty<LocalDateTime> createdAtProperty() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt.get();
    }
    
    public ObjectProperty<LocalDateTime> updatedAtProperty() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt.set(updatedAt);
    }
    
    @Override
    public String toString() {
        return contactUsername.get();
    }

}
