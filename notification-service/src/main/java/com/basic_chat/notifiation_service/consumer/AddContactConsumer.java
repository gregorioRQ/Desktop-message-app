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
			JsonNode senderNode = jsonNode.get("sender");
			JsonNode contactUsernameNode = jsonNode.get("contact_username");

			String sender = (senderNode != null && !senderNode.isNull()) ? senderNode.asText() : null;
			String contactUsername = (contactUsernameNode != null && !contactUsernameNode.isNull()) ? contactUsernameNode.asText() : null;

			if (sender == null || sender.isEmpty() || contactUsername == null || contactUsername.isEmpty()) {
				log.error("[ADD_CONTACT] Mensaje inválido: campos faltantes o vacíos. sender={}, contactUsername={}", sender, contactUsername);
				return;
			}

			log.info("[ADD_CONTACT] Procesando: {} agrega a {}", sender, contactUsername);

			ContactUser contact = new ContactUser(sender, contactUsername);
			contactUserRepository.save(contact);
			log.info("[ADD_CONTACT] Registro creado: {} -> {}", sender, contactUsername);

		} catch (Exception e) {
			log.error("[ADD_CONTACT_ERROR] Error procesando: {}", e.getMessage(), e);
		}
	}
}