package com.basic_chat.chat_service.util;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.context.SessionContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Clase de utilería para validaciones comunes en WebSocket handlers.
 * 
 * Centraliza validaciones repetidas como:
 * - Validación de contextos de sesión
 * - Validación de usernames
 * - Validación de IDs y datos
 * - Validación de sesiones WebSocket
 * 
 * Esta clase reduce duplicación de código y mejora la mantenibilidad.
 */
@Component
@Slf4j
public class WebSocketValidationUtil {

    // ==================== VALIDACIONES DE CONTEXTO ====================

    /**
     * Valida que el contexto de la sesión WebSocket sea válido.
     * 
     * Verifica:
     * - Context no es nulo
     * - WebSocketSession no es nula
     * - WebSocketSession está abierta
     * 
     * @param context contexto de sesión a validar
     * @return true si el contexto es válido, false en caso contrario
     */
    public boolean isValidContext(SessionContext context) {
        if (context == null) {
            log.debug("SessionContext es nulo");
            return false;
        }

        WebSocketSession session = context.getSession();
        if (session == null) {
            log.debug("WebSocketSession en contexto es nula");
            return false;
        }

        if (!session.isOpen()) {
            log.debug("WebSocketSession no está abierta");
            return false;
        }

        return true;
    }

    /**
     * Valida que una sesión WebSocket sea válida y esté abierta.
     * 
     * @param session sesión WebSocket a validar
     * @return true si la sesión es válida y está abierta, false en caso contrario
     */
    public boolean isValidWebSocketSession(WebSocketSession session) {
        if (session == null) {
            log.debug("WebSocketSession es nula");
            return false;
        }

        if (!session.isOpen()) {
            log.debug("WebSocketSession no está abierta");
            return false;
        }

        return true;
    }

    // ==================== VALIDACIONES DE STRINGS ====================

    /**
     * Valida que un string no sea nulo ni vacío.
     * 
     * @param value valor a validar
     * @param fieldName nombre del campo (para logs)
     * @return true si el valor es válido, false en caso contrario
     */
    public boolean isValidString(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            log.debug("{} es nulo o vacío", fieldName);
            return false;
        }
        return true;
    }

    /**
     * Valida múltiples strings de una sola vez.
     * 
     * @param values mapa de valores a validar (fieldName -> value)
     * @return true si todos los valores son válidos, false en caso contrario
     */
    public boolean isValidStrings(java.util.Map<String, String> values) {
        for (java.util.Map.Entry<String, String> entry : values.entrySet()) {
            if (!isValidString(entry.getValue(), entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Valida dos strings simultáneamente.
     * 
     * @param value1 primer valor
     * @param field1 nombre del primer campo
     * @param value2 segundo valor
     * @param field2 nombre del segundo campo
     * @return true si ambos valores son válidos, false en caso contrario
     */
    public boolean isValidStrings(String value1, String field1, String value2, String field2) {
        return isValidString(value1, field1) && isValidString(value2, field2);
    }

    /**
     * Valida tres strings simultáneamente.
     * 
     * @param value1 primer valor
     * @param field1 nombre del primer campo
     * @param value2 segundo valor
     * @param field2 nombre del segundo campo
     * @param value3 tercer valor
     * @param field3 nombre del tercer campo
     * @return true si todos los valores son válidos, false en caso contrario
     */
    public boolean isValidStrings(String value1, String field1, 
                                   String value2, String field2,
                                   String value3, String field3) {
        return isValidString(value1, field1) 
            && isValidString(value2, field2) 
            && isValidString(value3, field3);
    }

    // ==================== VALIDACIONES DE PROTOBUF ====================

    /**
     * Valida que un mensaje protobuf tenga un campo específico.
     * 
     * @param hasField resultado de hasXxx() del mensaje protobuf
     * @param fieldName nombre del campo (para logs)
     * @return true si el campo está presente, false en caso contrario
     */
    public boolean isValidProtobufField(boolean hasField, String fieldName) {
        if (!hasField) {
            log.warn("Campo protobuf no presente: {}", fieldName);
            return false;
        }
        return true;
    }

    // ==================== VALIDACIONES DE IDENTIDAD ====================

    /**
     * Valida que dos usernames sean diferentes (evita auto-operaciones).
     * 
     * @param user1 primer usuario
     * @param user2 segundo usuario
     * @return true si son diferentes, false si son iguales
     */
    public boolean areDifferentUsers(String user1, String user2) {
        if (user1.equalsIgnoreCase(user2)) {
            log.debug("Intento de auto-operación detectado - usuario: {}", user1);
            return false;
        }
        return true;
    }

    /**
     * Valida que un usuario no intente bloquearse a sí mismo.
     * 
     * @param blocker usuario que bloquea
     * @param blocked usuario bloqueado
     * @return true si son diferentes usuarios, false en caso contrario
     */
    public boolean isNotSelfBlock(String blocker, String blocked) {
        return areDifferentUsers(blocker, blocked);
    }

    // ==================== VALIDACIONES DE LISTAS ====================

    /**
     * Valida que una lista no sea nula ni vacía.
     * 
     * @param list lista a validar
     * @param fieldName nombre del campo (para logs)
     * @return true si la lista es válida, false en caso contrario
     */
    public boolean isValidList(java.util.List<?> list, String fieldName) {
        if (list == null || list.isEmpty()) {
            log.debug("{} es nula o vacía", fieldName);
            return false;
        }
        return true;
    }

    // ==================== VALIDACIONES COMBINADAS ====================

    /**
     * Valida un contactIdentity completo (recipient, senderId, senderUsername).
     * 
     * @param recipient nombre del usuario destinatario
     * @param senderId ID único del usuario remitente
     * @param senderUsername nombre de usuario del remitente
     * @return true si todos los datos son válidos, false en caso contrario
     */
    public boolean isValidContactIdentity(String recipient, String senderId, String senderUsername) {
        boolean valid = isValidStrings(
            recipient, "recipient",
            senderId, "senderId",
            senderUsername, "senderUsername"
        );

        if (!valid) {
            return false;
        }

        // Validar que el usuario no envía su propia identidad
        if (!areDifferentUsers(recipient, senderUsername)) {
            log.debug("Usuario intenta enviar su propia identidad - usuario: {}", recipient);
            return false;
        }

        return true;
    }

    /**
     * Valida una solicitud de bloqueo de usuario.
     * 
     * @param blocker usuario que realiza el bloqueo
     * @param blocked usuario a ser bloqueado
     * @return true si la solicitud es válida, false en caso contrario
     */
    public boolean isValidBlockRequest(String blocker, String blocked) {
        boolean valid = isValidStrings(blocker, "blocker", blocked, "blocked");
        
        if (!valid) {
            return false;
        }

        return isNotSelfBlock(blocker, blocked);
    }

    /**
     * Valida una solicitud de eliminar historial de chat.
     * 
     * @param sender usuario que solicita la eliminación
     * @param recipient usuario correspondiente
     * @return true si la solicitud es válida, false en caso contrario
     */
    public boolean isValidClearHistoryRequest(String sender, String recipient) {
        boolean valid = isValidStrings(sender, "sender", recipient, "recipient");
        
        if (!valid) {
            return false;
        }

        return areDifferentUsers(sender, recipient);
    }
}
