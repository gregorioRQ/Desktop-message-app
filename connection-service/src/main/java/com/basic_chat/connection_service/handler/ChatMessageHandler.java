package com.basic_chat.connection_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.connection_service.service.SessionRegistryService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ChatMessageHandler implements ConnectionWsMessageHandler {

    private final MessageRouterService messageRouterService;
    private final SessionRegistryService sessionRegistryService;

    public ChatMessageHandler(MessageRouterService messageRouterService, SessionRegistryService sessionRegistryService) {
        this.messageRouterService = messageRouterService;
        this.sessionRegistryService = sessionRegistryService;
    }

    /**
     * Verifica si este handler puede procesar el mensaje.
     * 
     * @param message Mensaje protobuf recibido
     * @return true si el mensaje contiene un ChatMessage
     */
    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasChatMessage();
    }

    /**
     * Procesa el mensaje de chat.
     * 
     * Extrae el destinatario del mensaje y lo envía a MessageRouterService
     * para su enrutamiento apropiado. Envía confirmación al remitente.
     * 
     * @param sender Username del usuario que envió el mensaje
     * @param message Mensaje protobuf a procesar
     */
    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.ChatMessage chatMessage = message.getChatMessage();
        String recipient = chatMessage.getRecipient();
        String messageId = chatMessage.getId();
        
        log.info("Procesando mensaje de chat de {} para {}", sender, recipient);
        
        // Serializar el mensaje completo para enviarlo
        byte[] messageData = message.toByteArray();
        
        // Enrutar el mensaje
        messageRouterService.routeMessage(sender, recipient, messageData);
        
        // Enviar confirmación al remitente
        sendConfirmationToSender(sender, messageId, recipient, true, null);
    }
    
    private void sendConfirmationToSender(String sender, String messageId, String recipient, boolean success, String errorMessage) {
        try {
            MessagesProto.ChatMessageResponse response = MessagesProto.ChatMessageResponse.newBuilder()
                    .setMessageId(messageId)
                    .setSuccess(success)
                    .setRecipient(recipient)
                    .setErrorMessage(errorMessage != null ? errorMessage : "")
                    .build();
            
            MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.newBuilder()
                    .setChatMessageResponse(response)
                    .build();
            
            sessionRegistryService.sendToUserByUsername(sender, wsMessage.toByteArray());
            log.debug("Confirmación enviada a {} para mensaje {}", sender, messageId);
        } catch (Exception e) {
            log.error("Error al enviar confirmación a {}: {}", sender, e.getMessage());
        }
    }
}
