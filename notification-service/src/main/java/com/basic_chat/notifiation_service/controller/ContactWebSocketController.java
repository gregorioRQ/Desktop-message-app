package com.basic_chat.notifiation_service.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controlador WebSocket para operaciones de contactos (LEGACY - COMENTADO).
 * 
 * Este controlador manejaba mensajes STOMP para agregar y eliminar contactos.
 * Fue comentado porque:
 * 1. Usa lógica de presencia que fue comentada
 * 2. Las operaciones de contactos se manejan de otra forma en el sistema
 * 
 * Mantenido por si se necesita en el futuro para referencia o reutilización.
 * 
 * @deprecated Controlador legacy - no utilizado.
 */
// @Controller
public class ContactWebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(ContactWebSocketController.class);

    /**
     * Constructor vacío para evitar errores de compilación.
     * El controlador ya no se usa activamente.
     */
    public ContactWebSocketController() {
        logger.info("ContactWebSocketController comentarios - no se ejecutará");
    }

    // private final NotificationService notificationService;

    // public ContactWebSocketController(NotificationService notificationService) {
    //     this.notificationService = notificationService;
    // }

    // /**
    //  * Recibe mensajes del cliente en el destino: /app/contact.add
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // @MessageMapping("/contact.add")
    // public void addContact(@Payload ContactAddEvent event) { ... }

    // /**
    //  * Recibe mensajes del cliente en el destino: /app/contact.drop
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // @MessageMapping("/contact.drop")
    // public void dropContacts(@Payload ContactDropEvent event) { ... }
}
