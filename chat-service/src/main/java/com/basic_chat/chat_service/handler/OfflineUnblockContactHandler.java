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
 * Handler offline para solicitudes de desbloqueo de contacto.
 * 
 * Cuando el destinatario está offline, este handler:
 * 1. Elimina el registro de la tabla contact_blocks (permanente)
 * 2. Guarda la solicitud como pendiente en pending_unblocks (para notificar cuando se conecte)
 * 
 * Este handler es necesario porque el patrón original de WebSocket directo
 * entre chat-service y cliente fue reemplazado por connection-service como gateway.
 * Ahora chat-service solo se comunica via RabbitMQ y REST, por lo que necesita
 * almacenar las notificaciones de desbloqueo para entregarlas cuando el cliente
 * se reconecte.
 * 
 * Lógica de arrepentimiento:
 * - Si el usuario envía UnblockContactRequest pero ya existe un PendingBlock pendiente
 *   (es decir, se arrepintió de bloquear), se elimina el PendingBlock y se guarda el nuevo PendingUnblock.
 * - Esto evita que el destinatario reciba una notificación de bloqueo cuando el remitente
 *   ya cambió de opinión y ahora quiere desbloquear.
 */
@Component
@Slf4j
public class OfflineUnblockContactHandler implements OfflineMessageHandler {

    private final PendingUnblockRepository pendingUnblockRepository;
    private final PendingBlockRepository pendingBlockRepository;
    private final BlockService blockService;

    public OfflineUnblockContactHandler(
            PendingUnblockRepository pendingUnblockRepository,
            PendingBlockRepository pendingBlockRepository,
            BlockService blockService) {
        this.pendingUnblockRepository = pendingUnblockRepository;
        this.pendingBlockRepository = pendingBlockRepository;
        this.blockService = blockService;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasUnblockContactRequest();
    }

    /**
     * Procesa una solicitud de desbloqueo de contacto para un destinatario offline.
     * 
     * Este método:
     * 1. Elimina el registro de contact_blocks (tabla permanente)
     * 2. Verifica si existe un PendingBlock previo (el usuario se arrepintió de bloquear)
     * 3. Si existe, lo elimina (lógica de arrepentimiento)
     * 4. Guarda el nuevo PendingUnblock (para notificar cuando se conecte)
     * 
     * @param message Mensaje protobuf que contiene UnblockContactRequest
     * @param recipient Username del destinatario (está offline)
     * @throws Exception si ocurre algún error al procesar
     */
    @Override
    @Transactional
    public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
        MessagesProto.UnblockContactRequest request = message.getUnblockContactRequest();
        
        // El campo 'blocker' contiene el usuario que envía la solicitud de desbloqueo
        // El campo 'recipient' contiene el usuario que será desbloqueado
        String blocker = request.getBlocker();
        
        // LOG CRÍTICO: Inicio de procesamiento de desbloqueo offline
        log.info("[OFFLINE_UNBLOCK] Bloqueador: {} → Desbloquear a: {}", blocker, recipient);
        
        // Paso 1: Eliminar el registro de contact_blocks (tabla permanente)
        // Esto asegura que el desbloqueo se aplique permanentemente
        // El método unblockUser es idempotente - si no existe, retorna true igualmente
        boolean unblockExecuted = blockService.unblockUser(blocker, recipient);
        if (unblockExecuted) {
            log.info("[OFFLINE_UNBLOCK_CONTACT] Registro de bloqueo eliminado de contact_blocks - blocker: {}, blocked: {}", blocker, recipient);
        } else {
            log.warn("[OFFLINE_UNBLOCK_CONTACT] No se pudo eliminar registro de contact_blocks - blocker: {}, blocked: {}", blocker, recipient);
        }
        
        // Paso 2: Verificar si existe un PendingBlock previo (arrepentimiento de bloqueo)
        // Si el usuario había enviado una solicitud de bloqueo pero ahora se arrepiente
        // y envía desbloqueo, eliminamos el bloqueo pendiente para que no llegue al receptor
        List<PendingBlock> pendingBlocks = pendingBlockRepository.findByBlockerAndBlockedUser(blocker, recipient);
        
        if (!pendingBlocks.isEmpty()) {
            // LOG CRÍTICO: Se encontró bloqueo pendiente - se elimina por arrepentimiento
            log.info("[OFFLINE_UNBLOCK_REPENT] Eliminando {} bloqueo(s) pendiente(s) - usuario {} se arrepintió de bloquear a {}", 
                    pendingBlocks.size(), blocker, recipient);
            pendingBlockRepository.deleteAll(pendingBlocks);
        }
        
        // Paso 3: Verificar si ya existe un PendingUnblock del mismo par (evitar duplicación)
        List<PendingUnblock> existingPendingUnblocks = pendingUnblockRepository.findByBlockerAndUnblockedUser(blocker, recipient);
        if (!existingPendingUnblocks.isEmpty()) {
            log.info("[OFFLINE_UNBLOCK_SKIP] Ya existe un PendingUnblock para blocker: {} y unblocked: {}, no se duplica", 
                    blocker, recipient);
            return;
        }
        
        // Paso 4: Guardar el nuevo desbloqueo pendiente (para notificar cuando se conecte)
        PendingUnblock pendingUnblock = new PendingUnblock();
        pendingUnblock.setBlocker(blocker);
        pendingUnblock.setUnblockedUser(recipient);
        pendingUnblock.setTimestamp(System.currentTimeMillis());
        
        pendingUnblockRepository.save(pendingUnblock);
        
        // LOG CRÍTICO: Desbloqueo pendiente guardado exitosamente
        log.info("[OFFLINE_UNBLOCK_SAVED] Desbloqueo pendiente guardado - desbloqueador: {}, desbloqueado: {}", 
                blocker, recipient);
    }
}
