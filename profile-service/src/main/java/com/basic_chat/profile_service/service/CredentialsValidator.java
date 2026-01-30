package com.basic_chat.profile_service.service;

import com.basic_chat.profile_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class CredentialsValidator {

    private final UserRepository userRepository;

    /**
     * Valida que el username no exista en la base de datos
     * @param username el nombre de usuario a validar
     * @return mensaje de error o null si es válido
     */
    public String validateUsernameNotExists(String username) {
        if (userRepository.existsByUsername(username)) {
            log.warn("Intento de registro con username existente: {}", username);
            return "Username existente";
        }
        return null;
    }

    /**
     * Valida la longitud mínima del username
     * @param username el nombre de usuario a validar
     * @return mensaje de error o null si es válido
     */
    public String validateUsernameLength(String username) {
        if (username.length() < 3) {
            log.warn("Username demasiado corto: {}", username);
            return "El nombre de usuario debe tener al menos 3 caracteres";
        }
        return null;
    }

    /**
     * Valida el formato del username (solo letras, números, guiones y guiones bajos)
     * @param username el nombre de usuario a validar
     * @return mensaje de error o null si es válido
     */
    public String validateUsernameFormat(String username) {
        if (!username.matches("^[a-zA-Z0-9_-]+$")) {
            log.warn("Username con caracteres no permitidos: {}", username);
            return "El nombre de usuario solo puede contener letras, números, guiones y guiones bajos";
        }
        return null;
    }

    /**
     * Valida la longitud mínima de la contraseña
     * @param password la contraseña a validar
     * @return mensaje de error o null si es válido
     */
    public String validatePasswordLength(String password) {
        if (password.length() < 6) {
            log.warn("Password demasiado corto");
            return "La contraseña debe tener al menos 6 caracteres";
        }
        return null;
    }

    /**
     * Realiza todas las validaciones de credenciales
     * @param username el nombre de usuario a validar
     * @param password la contraseña a validar
     * @return mensaje de error o null si todas las validaciones son válidas
     */
    public String validateCredentials(String username, String password) {
        String error = validateUsernameLength(username);
        if (error != null) return error;
        
        error = validateUsernameFormat(username);
        if (error != null) return error;
        
        error = validatePasswordLength(password);
        if (error != null) return error;
        
        error = validateUsernameNotExists(username);
        if (error != null) return error;
        
        return null;
    }
}
