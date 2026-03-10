package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.chat_service.models.PendingDeletion;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.chat_service.repository.PendingDeletionRepository;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler offline para solicitudes de eliminación de mensajes.
 * 
 * Cuando el destinatario está offline, verifica si el mensaje aún existe en la DB.
 * - Si existe: lo elimina directamente (no fue entregado aún).
 * - Si no existe: guarda la solicitud como pendiente (ya fue entregado).
 */
@Component
@Slf4j
public class OfflineDeleteMessageHandler implements OfflineMessageHandler {

    private final PendingDeletionRepository pendingDeletionRepository;
    private final MessageRepository messageRepository;

    public OfflineDeleteMessageHandler(PendingDeletionRepository pendingDeletionRepository,
                                     MessageRepository messageRepository) {
        this.pendingDeletionRepository = pendingDeletionRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasDeleteMessageRequest();
    }

    @Override
    public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
        MessagesProto.DeleteMessageRequest request = message.getDeleteMessageRequest();
        String messageIdStr = request.getMessageId();
        
        try {
            Long messageId = Long.valueOf(messageIdStr);
            
            // Escenario 2: El mensaje aún está en la DB (no ha sido entregado)
            if (messageRepository.existsById(messageId)) {
                messageRepository.deleteById(messageId);
                log.info("Mensaje {} eliminado de la DB (no entregado) para usuario offline: {}", messageId, recipient);
                return;
            }
        } catch (NumberFormatException e) {
            log.warn("ID de mensaje inválido recibido en handler offline: {}", messageIdStr);
        }

        // Escenario 1: El mensaje ya no está en la DB (fue entregado), guardar notificación pendiente
        PendingDeletion pendingDeletion = new PendingDeletion();
        pendingDeletion.setRecipient(recipient);
        pendingDeletion.setMessageId(messageIdStr);
        pendingDeletion.setDeletedBy(request.getSenderUsername());
        
        pendingDeletionRepository.save(pendingDeletion);
        
        log.info("Eliminación pendiente guardada - mensaje: {}, para: {}", messageIdStr, recipient);
    }
}
