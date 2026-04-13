package com.basic_chat.notifiation_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@NoArgsConstructor
@AllArgsConstructor
public class User {
    
    @Id
    private String id;

    @Column(name = "username", unique = true, nullable = false)
    private String username;

    /**
     * Indica si el usuario está actualmente conectado al sistema.
     * Este campo se actualiza cuando el usuario se conecta o desconecta
     * a través del connection-service.
     * 
     * Valor por defecto: false (offline)
     */
    @Column(name = "online")
    private boolean online = false;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }
}
