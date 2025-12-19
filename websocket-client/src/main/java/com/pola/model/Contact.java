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
    private final StringProperty contactNickname;
    private final StringProperty contactUsername;
    private final BooleanProperty blocked;
    private final ObjectProperty<LocalDateTime> createdAt;
    private final ObjectProperty<LocalDateTime> updatedAt;
    
    public Contact(int id, String userId, String contactNickname, 
                   String contactUsername, boolean blocked,
                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = new SimpleIntegerProperty(id);
        this.userId = new SimpleStringProperty(userId);
        this.contactNickname = new SimpleStringProperty(contactNickname);
        this.contactUsername = new SimpleStringProperty(contactUsername);
        this.blocked = new SimpleBooleanProperty(blocked);
        this.createdAt = new SimpleObjectProperty<>(createdAt);
        this.updatedAt = new SimpleObjectProperty<>(updatedAt);
    }
    
    // Constructor simplificado para crear nuevos contactos
    public Contact(String userId, String contactNickname, String contactUsername) {
        this(0, userId, contactNickname, contactUsername, false, 
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
    
    public String getContactNickname() {
        return contactNickname.get();
    }
    
    public StringProperty contactNicknamProperty() {
        return contactNickname;
    }
    
    public void setContactNickname(String contactNickname) {
        this.contactNickname.set(contactNickname);
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

    // muestra el apodo si existe sino el username
    public String getDisplayName(){
        return contactNickname != null && !contactNickname.get().isEmpty()
        ? contactNickname.get() : contactUsername.get();
    }
}
