
/**
 * ContactWebSocketController expone endpoints WebSocket para gestionar la relación de contactos entre usuarios.
 * Permite agregar y eliminar contactos en tiempo real, recibiendo eventos desde el cliente y delegando la lógica
 * al NotificationService. Facilita la interacción dinámica de la agenda de contactos en la aplicación de mensajería.
 */
package com.basic_chat.notifiation_service.controller;

import com.basic_chat.notifiation_service.model.ContactAddEvent;
import com.basic_chat.notifiation_service.model.ContactDropEvent;
import com.basic_chat.notifiation_service.service.NotificationService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

@Controller
public class ContactWebSocketController {

    private final NotificationService notificationService;

    public ContactWebSocketController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Recibe mensajes del cliente en el destino: /app/contact.add
     * Payload esperado: JSON compatible con ContactAddEvent { "from": "user", "to": "contact" }
     */
    @MessageMapping("/contact.add")
    public void addContact(@Payload ContactAddEvent event) {
        // Llama al servicio para guardar la relación en la base de datos
        notificationService.addContact(event.getFrom(), event.getTo());
    }

    /**
     * Recibe mensajes del cliente en el destino: /app/contact.drop
     * Payload esperado: JSON compatible con ContactDropEvent { "userId": "...", "contactIds": ["...", "..."] }
     */
    @MessageMapping("/contact.drop")
    public void dropContacts(@Payload ContactDropEvent event) {
        // Llama al servicio para eliminar los contactos y limpiar suscripciones
        notificationService.removeContacts(event.getUserId(), event.getContactIds());
    }
}
