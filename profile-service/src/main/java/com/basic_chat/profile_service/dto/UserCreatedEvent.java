package com.basic_chat.profile_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento de usuario creado para enviar a notification-service.
 * 
 * Contiene el user_id y username necesarios para que notification-service
 * pueda crear el registro del usuario en su tabla de usuarios.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCreatedEvent {
    private String user_id;
    private String username;
}