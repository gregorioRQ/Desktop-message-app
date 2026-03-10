package com.basic_chat.connection_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PendingMessagesService {

    private final RestTemplate restTemplate;
    private final String chatServiceUrl;

    public PendingMessagesService(
            @Value("${chat.service.url:http://localhost:8085}") String chatServiceUrl) {
        this.restTemplate = new RestTemplate();
        this.chatServiceUrl = chatServiceUrl;
    }

    public List<byte[]> getPendingMessages(String username) {
        try {
            String url = chatServiceUrl + "/api/v1/messages/pending/" + username;

            log.info("Fetching pending messages from: {}", url);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                log.warn("Failed to fetch pending messages for {}", username);
                return List.of();
            }

            @SuppressWarnings("unchecked")
            List<String> encodedMessages = (List<String>) response.get("messages");

            if (encodedMessages == null || encodedMessages.isEmpty()) {
                log.info("No pending messages for {}", username);
                return List.of();
            }

            log.info("Found pending messages for {}: {} messages", username, encodedMessages.size());

            return encodedMessages.stream()
                    .map(Base64.getDecoder()::decode)
                    .toList();

        } catch (Exception e) {
            log.error("Error fetching pending messages for {}: {}", username, e.getMessage());
            return List.of();
        }
    }

    /**
     * Obtiene el destinatario de un mensaje específico.
     * 
     * Este método consulta a chat-service para obtener la información del mensaje
     * y determinar quién es el destinatario. Se usa para saber a quién notificar
     * cuando se procesa una solicitud de eliminación de mensaje.
     * 
     * @param messageId ID del mensaje
     * @return Username del destinatario, o null si no se encontró el mensaje
     */
    public String getMessageRecipient(String messageId) {
        try {
            String url = chatServiceUrl + "/api/v1/messages/" + messageId + "/recipient";
            
            log.info("Fetching message recipient from: {}", url);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            
            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                log.warn("Failed to fetch message recipient for message {}", messageId);
                return null;
            }
            
            String recipient = (String) response.get("recipient");
            log.info("Message {} recipient is: {}", messageId, recipient);
            return recipient;
            
        } catch (Exception e) {
            log.error("Error fetching message recipient for {}: {}", messageId, e.getMessage());
            return null;
        }
    }
}
