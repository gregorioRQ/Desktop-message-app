package com.basic_chat.notifiation_service.model;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Evento de usuario creado recibido desde profile-service.
 * 
 * Contiene el userId y username necesarios para crear el registro
 * del usuario en la tabla de usuarios de notification-service.
 */
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateEvent {
    private String user_id;
    private String username;

    public String getUser_id() {
        return user_id;
    }

    public void setUser_id(String user_id) {
        this.user_id = user_id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
