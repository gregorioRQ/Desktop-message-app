package com.basic_chat.connection_service.handler;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.connection_service.service.SessionRegistryService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class ContactPresenceHandler implements ConnectionWsMessageHandler {

    private final SessionRegistryService sessionRegistryService;
    private final RestTemplate restTemplate;

    @Value("${notification.service.url:http://localhost:8084}")
    private String notificationServiceUrl;

    public ContactPresenceHandler(SessionRegistryService sessionRegistryService) {
        this.sessionRegistryService = sessionRegistryService;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasContactPresenceMessage();
    }

    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.ContactPresenceMessage contactPresenceMessage = message.getContactPresenceMessage();
        
        if (!contactPresenceMessage.hasRequest()) {
            log.warn(" Mensaje ContactPresence sin request de: {}", sender);
            return;
        }

        MessagesProto.ContactPresenceRequest request = contactPresenceMessage.getRequest();
        String userId = request.getUserId();
        
        log.info("Solicitando presencia de contactos para userId: {}", userId);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/x-protobuf"));
            headers.set("X-User-Id", userId);
            
            HttpEntity<byte[]> requestEntity = new HttpEntity<>(new byte[0], headers);
            
            org.springframework.http.ResponseEntity<byte[]> response = restTemplate.exchange(
                notificationServiceUrl + "/api/presence/contacts",
                org.springframework.http.HttpMethod.GET,
                requestEntity,
                byte[].class
            );
            
            MessagesProto.ContactPresenceResponse presenceResponse = MessagesProto.ContactPresenceResponse.parseFrom(response.getBody());
            
            SessionRegistryService.SessionInfo sessionInfo = sessionRegistryService.getSessionByUserId(userId);
            if (sessionInfo != null) {
                WebSocketSession session = sessionInfo.getSession();
                MessagesProto.ContactPresenceMessage responseMessage = MessagesProto.ContactPresenceMessage.newBuilder()
                    .setResponse(presenceResponse)
                    .build();
                
                session.sendMessage(new org.springframework.web.socket.BinaryMessage(responseMessage.toByteArray()));
                log.info("Respuesta de presencia enviada a userId: {}", userId);
            }
            
        } catch (Exception e) {
            log.error("Error al obtener presencia de contactos: {}", e.getMessage());
        }
    }
}