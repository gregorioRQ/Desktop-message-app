package com.basic_chat.chat_service.handler;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.chat_service.models.PendingBlock;
import com.basic_chat.chat_service.models.PendingUnblock;
import com.basic_chat.chat_service.repository.PendingBlockRepository;
import com.basic_chat.chat_service.repository.PendingUnblockRepository;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler offline para solicitudes de bloqueo de contacto.
 * 
 * Cuando el destinatario está offline, guarda la solicitud como pendiente.
 * 
 * Lógica de arrepentimiento:
 * - Si el usuario envía BlockContactRequest pero ya existe un PendingUnblock pendiente
 *   (es decir, se arrepintió de desbloquear), se elimina el PendingUnblock y se guarda el nuevo PendingBlock.
 * - Esto evita que el destinatario reciba una notificación de desbloqueo cuando el remitente
 *   ya cambió de opinión y ahora quiere bloquear.
 */
@Component
@Slf4j
public class OfflineBlockContactHandler implements OfflineMessageHandler {

    private final PendingBlockRepository pendingBlockRepository;
    private final PendingUnblockRepository pendingUnblockRepository;

    public OfflineBlockContactHandler(
            PendingBlockRepository pendingBlockRepository,
            PendingUnblockRepository pendingUnblockRepository) {
        this.pendingBlockRepository = pendingBlockRepository;
        this.pendingUnblockRepository = pendingUnblockRepository;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasBlockContactRequest();
    }

    /**
     * Procesa una solicitud de bloqueo de contacto para un destinatario offline.
     * 
     * Este método:
     * 1. Verifica si existe un PendingUnblock previo (el usuario se arrepintió de desbloquear)
     * 2. Si existe, lo elimina (lógica de arrepentimiento)
     * 3. Guarda el nuevo PendingBlock
     * 
     * @param message Mensaje protobuf que contiene BlockContactRequest
     * @param recipient Username del destinatario (está offline)
     * @throws Exception si ocurre algún error al procesar
     */
    @Override
    @Transactional
    public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
        MessagesProto.BlockContactRequest request = message.getBlockContactRequest();
        String sender = request.getRecipient(); // Usuario que quiere bloquear
        
        // LOG CRÍTICO: Inicio de procesamiento de bloqueo offline
        log.info("[OFFLINE_BLOCK] Remitente: {} → Bloquear a: {}", sender, recipient);
        
        // Verificar si existe un PendingUnblock previo (arrepentimiento de desbloecko)
        // Si el usuario había enviado una solicitud de desbloqueo pero ahora se arrepiente
        // y envía bloqueo, eliminamos el desbloqueo pendiente para que no llegue al receptor
        List<PendingUnblock> pendingUnblocks = pendingUnblockRepository.findByBlockerAndUnblockedUser(sender, recipient);
        
        if (!pendingUnblocks.isEmpty()) {
            // LOG CRÍTICO: Se encontró desbloqueo pendiente - se elimina por arrepentimiento
            log.info("[OFFLINE_BLOCK_REPENT] Eliminando {} desbloqueo(s) pendiente(s) - usuario {} se arrepintió de desbloquear a {}", 
                    pendingUnblocks.size(), sender, recipient);
            pendingUnblockRepository.deleteAll(pendingUnblocks);
        }
        
        // Guardar el nuevo bloqueo pendiente
        PendingBlock pendingBlock = new PendingBlock();
        pendingBlock.setBlocker(sender);
        pendingBlock.setBlockedUser(recipient);
        pendingBlock.setTimestamp(System.currentTimeMillis());
        
        pendingBlockRepository.save(pendingBlock);
        
        // LOG CRÍTICO: Bloqueo pendiente guardado exitosamente
        log.info("[OFFLINE_BLOCK_SAVED] Bloqueo pendiente guardado - bloqueador: {}, bloqueado: {}", sender, recipient);
    }
}
