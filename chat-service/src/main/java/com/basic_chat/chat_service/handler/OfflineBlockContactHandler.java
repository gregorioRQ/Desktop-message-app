package com.basic_chat.chat_service.handler;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.chat_service.models.PendingBlock;
import com.basic_chat.chat_service.models.PendingUnblock;
import com.basic_chat.chat_service.repository.PendingBlockRepository;
import com.basic_chat.chat_service.repository.PendingUnblockRepository;
import com.basic_chat.chat_service.service.BlockService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler offline para solicitudes de bloqueo de contacto.
 * 
 * Cuando el destinatario está offline, este handler:
 * 1. Registra el bloqueo en la tabla contact_blocks (permanente)
 * 2. Guarda la solicitud como pendiente en pending_blocks (para notificar cuando se conecte)
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
    private final BlockService blockService;

    public OfflineBlockContactHandler(
            PendingBlockRepository pendingBlockRepository,
            PendingUnblockRepository pendingUnblockRepository,
            BlockService blockService) {
        this.pendingBlockRepository = pendingBlockRepository;
        this.pendingUnblockRepository = pendingUnblockRepository;
        this.blockService = blockService;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasBlockContactRequest();
    }

    /**
     * Procesa una solicitud de bloqueo de contacto para un destinatario offline.
     * 
     * Este método:
     * 1. Registra el bloqueo en contact_blocks (tabla permanente)
     * 2. Verifica si existe un PendingUnblock previo (el usuario se arrepintió de desbloquear)
     * 3. Si existe, lo elimina (lógica de arrepentimiento)
     * 4. Guarda el nuevo PendingBlock (para notificar cuando se conecte)
     * 
     * @param message Mensaje protobuf que contiene BlockContactRequest
     * @param recipient Username del destinatario (está offline)
     * @throws Exception si ocurre algún error al procesar
     */
    @Override
    @Transactional
    public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
        MessagesProto.BlockContactRequest request = message.getBlockContactRequest();
        
        // El campo 'blocker' contiene el usuario que envía la solicitud de bloqueo
        // El campo 'recipient' contiene el usuario que será bloqueado
        String blocker = request.getBlocker();
        
        // LOG CRÍTICO: Inicio de procesamiento de bloqueo offline
        log.info("[OFFLINE_BLOCK] Bloqueador: {} → Bloquear a: {}", blocker, recipient);
        
        // Paso 1: Registrar el bloqueo en contact_blocks (tabla permanente)
        // Esto asegura que el bloqueo persista incluso si el destinatario está offline
        // El método blockUser es idempotente - si ya existe, retorna true sin duplicar
        boolean blockRegistered = blockService.blockUser(blocker, recipient);
        if (blockRegistered) {
            log.info("[OFFLINE_BLOCK_CONTACT] Registro de bloqueo creado en contact_blocks - blocker: {}, blocked: {}", blocker, recipient);
        } else {
            log.warn("[OFFLINE_BLOCK_CONTACT] No se pudo registrar bloqueo en contact_blocks - blocker: {}, blocked: {}", blocker, recipient);
        }
        
        // Paso 2: Verificar si existe un PendingUnblock previo (arrepentimiento de desbloqueo)
        // Si el usuario había enviado una solicitud de desbloqueo pero ahora se arrepiente
        // y envía bloqueo, eliminamos el desbloqueo pendiente para que no llegue al receptor
        List<PendingUnblock> pendingUnblocks = pendingUnblockRepository.findByBlockerAndUnblockedUser(blocker, recipient);
        
        if (!pendingUnblocks.isEmpty()) {
            // LOG CRÍTICO: Se encontró desbloqueo pendiente - se elimina por arrepentimiento
            log.info("[OFFLINE_BLOCK_REPENT] Eliminando {} desbloqueo(s) pendiente(s) - usuario {} se arrepintió de desbloquear a {}", 
                    pendingUnblocks.size(), blocker, recipient);
            pendingUnblockRepository.deleteAll(pendingUnblocks);
        }
        
        // Paso 3: Verificar si ya existe un PendingBlock del mismo par (evitar duplicación)
        List<PendingBlock> existingPendingBlocks = pendingBlockRepository.findByBlockerAndBlockedUser(blocker, recipient);
        if (!existingPendingBlocks.isEmpty()) {
            log.info("[OFFLINE_BLOCK_SKIP] Ya existe un PendingBlock para blocker: {} y blocked: {}, no se duplica", 
                    blocker, recipient);
            return;
        }
        
        // Paso 4: Guardar el nuevo bloqueo pendiente (para notificar cuando se conecte)
        PendingBlock pendingBlock = new PendingBlock();
        pendingBlock.setBlocker(blocker);
        pendingBlock.setBlockedUser(recipient);
        pendingBlock.setTimestamp(System.currentTimeMillis());
        
        pendingBlockRepository.save(pendingBlock);
        
        // LOG CRÍTICO: Bloqueo pendiente guardado exitosamente
        log.info("[OFFLINE_BLOCK_SAVED] Bloqueo pendiente guardado - bloqueador: {}, bloqueado: {}", blocker, recipient);
    }
}
