package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.chat_service.models.PendingBlock;
import com.basic_chat.chat_service.repository.PendingBlockRepository;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler offline para solicitudes de bloqueo de contacto.
 * 
 * Cuando el destinatario está offline, guarda la solicitud como pendiente.
 */
@Component
@Slf4j
public class OfflineBlockContactHandler implements OfflineMessageHandler {

    private final PendingBlockRepository pendingBlockRepository;

    public OfflineBlockContactHandler(PendingBlockRepository pendingBlockRepository) {
        this.pendingBlockRepository = pendingBlockRepository;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasBlockContactRequest();
    }

    @Override
    @Transactional
    public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
        MessagesProto.BlockContactRequest request = message.getBlockContactRequest();
        
        PendingBlock pendingBlock = new PendingBlock();
        pendingBlock.setBlocker(request.getRecipient());
        pendingBlock.setBlockedUser(recipient);
        pendingBlock.setTimestamp(System.currentTimeMillis());
        
        pendingBlockRepository.save(pendingBlock);
        
        log.info("Bloqueo pendiente guardado - bloqueador: {}, bloqueado: {}", request.getRecipient(), recipient);
    }
}
