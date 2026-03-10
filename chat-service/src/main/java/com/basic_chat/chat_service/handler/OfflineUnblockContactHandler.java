package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.chat_service.models.PendingUnblock;
import com.basic_chat.chat_service.repository.PendingUnblockRepository;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler offline para solicitudes de desbloqueo de contacto.
 * 
 * Cuando el destinatario está offline, guarda la solicitud de desbloqueo
 * como pendiente para ser notificado cuando se conecte.
 * 
 * Este handler es necesario porque el patrón original de WebSocket directo
 * entre chat-service y cliente fue reemplazado por connection-service como gateway.
 * Ahora chat-service solo se comunica via RabbitMQ y REST, por lo que necesita
 * almacenar las notificaciones de desbloqueo para entregarlas cuando el cliente
 * se reconecte.
 */
@Component
@Slf4j
public class OfflineUnblockContactHandler implements OfflineMessageHandler {

    private final PendingUnblockRepository pendingUnblockRepository;

    public OfflineUnblockContactHandler(PendingUnblockRepository pendingUnblockRepository) {
        this.pendingUnblockRepository = pendingUnblockRepository;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasUnblockContactRequest();
    }

    @Override
    public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
        MessagesProto.UnblockContactRequest request = message.getUnblockContactRequest();
        
        PendingUnblock pendingUnblock = new PendingUnblock();
        pendingUnblock.setBlocker(request.getRecipient());
        pendingUnblock.setUnblockedUser(recipient);
        pendingUnblock.setTimestamp(System.currentTimeMillis());
        
        pendingUnblockRepository.save(pendingUnblock);
        
        log.info("Desbloqueo pendiente guardado - desbloqueador: {}, desbloqueado: {}", 
                request.getRecipient(), recipient);
    }
}
