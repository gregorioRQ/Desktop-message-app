package com.basic_chat.notifiation_service.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.basic_chat.notifiation_service.model.ContactUser;
import com.basic_chat.notifiation_service.repository.ContactUserRepository;

@Component
public class AddContactConsumer {

    private static final Logger log = LoggerFactory.getLogger(AddContactConsumer.class);

    private final ContactUserRepository contactUserRepository;

    public AddContactConsumer(ContactUserRepository contactUserRepository) {
        this.contactUserRepository = contactUserRepository;
        log.info("AddContactConsumer inicializado");
    }

    @RabbitListener(queuesToDeclare = @org.springframework.amqp.rabbit.annotation.Queue("contact.events"))
    public void handleAddContact(String message) {
        log.info("[ADD_CONTACT] Mensaje recibido: {}", message);

        try {
            String sender = extractJsonField(message, "sender");
            String contactUsername = extractJsonField(message, "contact_username");

            if (sender == null || contactUsername == null) {
                log.error("[ADD_CONTACT] Mensaje inválido: campos faltantes");
                return;
            }

            log.info("[ADD_CONTACT] Procesando: {} agrega a {}", sender, contactUsername);

            // Crear registro sender -> contactUsername
            ContactUser contact1 = new ContactUser(sender, contactUsername);
            contactUserRepository.save(contact1);
            log.info("[ADD_CONTACT] Registro creado: {} -> {}", sender, contactUsername);

            // Crear registro bidireccional contactUsername -> sender
            ContactUser contact2 = new ContactUser(contactUsername, sender);
            contactUserRepository.save(contact2);
            log.info("[ADD_CONTACT] Registro creado: {} -> {}", contactUsername, sender);

        } catch (Exception e) {
            log.error("[ADD_CONTACT_ERROR] Error procesando: {}", e.getMessage(), e);
        }
    }

    private String extractJsonField(String json, String field) {
        String searchKey = "\"" + field + "\":\"";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1) {
            return null;
        }
        int start = keyIndex + searchKey.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : null;
    }
}