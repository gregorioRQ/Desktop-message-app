package com.basic_chat.chat_service.service;

import com.basic_chat.proto.MessagesProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DeliveryService {

    private final MessageService messageService;
    private final BlockService blockService;

    public DeliveryService(MessageService messageService, BlockService blockService) {
        this.messageService = messageService;
        this.blockService = blockService;
    }

    /**
     * Procesa un mensaje de chat.
     * 
     * Este método:
     * 1. Verifica si el remitente está bloqueado por el destinatario
     * 2. Guarda el mensaje en la base de datos
     * 3. Notifica a connection-service sobre el estado de entrega
     * 
     * Nota: La notificación de entrega ahora se maneja en connection-service
     * cuando el mensaje se encola para un usuario offline.
     * 
     * @param wsMessage Mensaje completo WsMessage
     * @param chatMessage Mensaje de chat parsed
     */
    public void processMessage(MessagesProto.WsMessage wsMessage, MessagesProto.ChatMessage chatMessage) {
        String sender = chatMessage.getSender();
        String recipient = chatMessage.getRecipient();

        if (isBlocked(sender, recipient)) {
            log.warn("Blocked message from {} to {}", sender, recipient);
            // El mensaje bloqueado se notifica via connection-service
            return;
        }

        saveMessage(chatMessage);
        log.debug("Message processed and saved: ID={}, from={}, to={}", 
                chatMessage.getId(), sender, recipient);
    }

    private boolean isBlocked(String sender, String recipient) {
        try {
            return blockService.isBlocked(sender, recipient);
        } catch (Exception e) {
            log.error("Error checking block status between {} and {}", sender, recipient, e);
            return false;
        }
    }

    private void saveMessage(MessagesProto.ChatMessage chatMessage) {
        try {
            messageService.saveMessage(chatMessage);
            log.debug("Message ID: {} saved to database.", chatMessage.getId());
        } catch (Exception e) {
            log.error("Failed to save message ID: {}", chatMessage.getId(), e);
        }
    }
}
