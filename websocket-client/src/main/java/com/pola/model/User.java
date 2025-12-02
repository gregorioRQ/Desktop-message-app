package com.pola.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class User {
    private final StringProperty id;
    private final StringProperty username;
    private final BooleanProperty online;
    
    public User(String id, String username, boolean online) {
        this.id = new SimpleStringProperty(id);
        this.username = new SimpleStringProperty(username);
        this.online = new SimpleBooleanProperty(online);
    }
    
    // Getters y setters
    public String getId() {
        return id.get();
    }
    
    public void setId(String id) {
        this.id.set(id);
    }
    
    public StringProperty idProperty() {
        return id;
    }
    
    public String getUsername() {
        return username.get();
    }
    
    public void setUsername(String username) {
        this.username.set(username);
    }
    
    public StringProperty usernameProperty() {
        return username;
    }
    
    public boolean isOnline() {
        return online.get();
    }
    
    public void setOnline(boolean online) {
        this.online.set(online);
    }
    
    public BooleanProperty onlineProperty() {
        return online;
    }
}
