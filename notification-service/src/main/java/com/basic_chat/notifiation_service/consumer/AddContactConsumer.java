package com.basic_chat.notifiation_service.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.basic_chat.notifiation_service.model.ContactUser;
import com.basic_chat.notifiation_service.repository.ContactUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class AddContactConsumer {

    private static final Logger log = LoggerFactory.getLogger(AddContactConsumer.class);

    private final ContactUserRepository contactUserRepository;
    private final ObjectMapper objectMapper;

    public AddContactConsumer(ContactUserRepository contactUserRepository) {
        this.contactUserRepository = contactUserRepository;
        this.objectMapper = new ObjectMapper();
        log.info("AddContactConsumer inicializado");
    }

    @RabbitListener(queuesToDeclare = @org.springframework.amqp.rabbit.annotation.Queue("contact.events"))
    public void handleAddContact(String message) {
        log.info("[ADD_CONTACT] Mensaje recibido: {}", message);

        try {
            JsonNode jsonNode = objectMapper.readTree(message);
            String sender = jsonNode.has("sender") ? jsonNode.get("sender").asText() : null;
            String contactUsername = jsonNode.has("contact_username") ? jsonNode.get("contact_username").asText() : null;

            if (sender == null || contactUsername == null) {
                log.error("[ADD_CONTACT] Mensaje inválido: campos faltantes. sender={}, contactUsername={}", sender, contactUsername);
                return;
            }

            log.info("[ADD_CONTACT] Procesando: {} agrega a {}", sender, contactUsername);

            ContactUser contact1 = new ContactUser(sender, contactUsername);
            contactUserRepository.save(contact1);
            log.info("[ADD_CONTACT] Registro creado: {} -> {}", sender, contactUsername);

            ContactUser contact2 = new ContactUser(contactUsername, sender);
            contactUserRepository.save(contact2);
            log.info("[ADD_CONTACT] Registro creado: {} -> {}", contactUsername, sender);

        } catch (Exception e) {
            log.error("[ADD_CONTACT_ERROR] Error procesando: {}", e.getMessage(), e);
        }
    }
}