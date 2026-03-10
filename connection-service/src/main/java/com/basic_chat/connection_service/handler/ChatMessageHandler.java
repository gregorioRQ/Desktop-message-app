package com.basic_chat.connection_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler para mensajes de chat (ChatMessage).
 * 
 * Este handler procesa los mensajes de chat enviados por el cliente y los enruta
 * al destinatario apropiado usando MessageRouterService.
 * 
 * El flujo es:
 * Cliente → connection-service (este handler) → MessageRouterService → RabbitMQ → chat-service
 */
@Component
@Slf4j
public class ChatMessageHandler implements ConnectionWsMessageHandler {

    private final MessageRouterService messageRouterService;

    public ChatMessageHandler(MessageRouterService messageRouterService) {
        this.messageRouterService = messageRouterService;
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
     * para su enrutamiento apropiado.
     * 
     * @param sender Username del usuario que envió el mensaje
     * @param message Mensaje protobuf a procesar
     */
    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.ChatMessage chatMessage = message.getChatMessage();
        String recipient = chatMessage.getRecipient();
        
        log.info("Procesando mensaje de chat de {} para {}", sender, recipient);
        
        // Serializar el mensaje completo para enviarlo
        byte[] messageData = message.toByteArray();
        
        // Enrutar el mensaje
        messageRouterService.routeMessage(sender, recipient, messageData);
    }
}
