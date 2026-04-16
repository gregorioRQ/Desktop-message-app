package com.basic_chat.connection_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler para mensajes de imagen (ImageMessage).
 * 
 * Este handler procesa las notificaciones de imagen enviadas por el cliente emisor.
 * El flujo es:
 * Cliente emisor -> connection-service (este handler) -> MessageRouterService -> 
 *   - Si receptor online: enviar directo por WebSocket
 *   - Si receptor offline: encolar a chat-service para guardar pending + notificar
 * 
 * La imagen ya esta almacenada en media-service, este handler solo notifica al
 * receptor que hay una imagen disponible para descargar.
 */
@Component
@Slf4j
public class ImageMessageHandler implements ConnectionWsMessageHandler {

    private final MessageRouterService messageRouterService;

    public ImageMessageHandler(MessageRouterService messageRouterService) {
        this.messageRouterService = messageRouterService;
    }

    /**
     * Verifica si este handler puede procesar el mensaje.
     * 
     * @param message Mensaje protobuf recibido
     * @return true si el mensaje contiene un ImageMessage
     */
    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasImageMessage();
    }

    /**
     * Procesa el mensaje de imagen.
     * 
     * Extrae el receptor del mensaje y lo envia a MessageRouterService
     * para su enrutamiento apropiado (similar a ChatMessage).
     * 
     * @param sender Username del usuario que envio la imagen
     * @param message Mensaje protobuf a procesar
     */
    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.ImageMessage imageMessage = message.getImageMessage();
        String receiverId = imageMessage.getReceiverId();
        
        log.info("Procesando mensaje de imagen de {} para {}, mediaId: {}", 
            sender, receiverId, imageMessage.getMediaId());
        
        // Serializar el mensaje completo para enviarlo
        byte[] messageData = message.toByteArray();
        
        // Enrutar el mensaje de imagen (mismo flujo que ChatMessage)
        messageRouterService.routeImageMessage(sender, receiverId, messageData);
    }
}
