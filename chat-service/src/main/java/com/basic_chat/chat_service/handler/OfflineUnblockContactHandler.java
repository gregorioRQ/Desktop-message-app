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

    public OfflineUnblockContactHandler(
            PendingUnblockRepository pendingUnblockRepository,
            PendingBlockRepository pendingBlockRepository) {
        this.pendingUnblockRepository = pendingUnblockRepository;
        this.pendingBlockRepository = pendingBlockRepository;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasUnblockContactRequest();
    }

    /**
     * Procesa una solicitud de desbloqueo de contacto para un destinatario offline.
     * 
     * Este método:
     * 1. Verifica si existe un PendingBlock previo (el usuario se arrepintió de bloquear)
     * 2. Si existe, lo elimina (lógica de arrepentimiento)
     * 3. Guarda el nuevo PendingUnblock
     * 
     * @param message Mensaje protobuf que contiene UnblockContactRequest
     * @param recipient Username del destinatario (está offline)
     * @throws Exception si ocurre algún error al procesar
     */
    @Override
    @Transactional
    public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
        MessagesProto.UnblockContactRequest request = message.getUnblockContactRequest();
        String sender = request.getRecipient(); // Usuario que quiere desbloquear
        
        // LOG CRÍTICO: Inicio de procesamiento de desbloqueo offline
        log.info("[OFFLINE_UNBLOCK] Remitente: {} → Desbloquear a: {}", sender, recipient);
        
        // Verificar si existe un PendingBlock previo (arrepentimiento de bloqueo)
        // Si el usuario había enviado una solicitud de bloqueo pero ahora se arrepiente
        // y envía desbloqueo, eliminamos el bloqueo pendiente para que no llegue al receptor
        List<PendingBlock> pendingBlocks = pendingBlockRepository.findByBlockerAndBlockedUser(sender, recipient);
        
        if (!pendingBlocks.isEmpty()) {
            // LOG CRÍTICO: Se encontró bloqueo pendiente - se elimina por arrepentimiento
            log.info("[OFFLINE_UNBLOCK_REPENT] Eliminando {} bloqueo(s) pendiente(s) - usuario {} se arrepintió de bloquear a {}", 
                    pendingBlocks.size(), sender, recipient);
            pendingBlockRepository.deleteAll(pendingBlocks);
        }
        
        // Guardar el nuevo desbloqueo pendiente
        PendingUnblock pendingUnblock = new PendingUnblock();
        pendingUnblock.setBlocker(sender);
        pendingUnblock.setUnblockedUser(recipient);
        pendingUnblock.setTimestamp(System.currentTimeMillis());
        
        pendingUnblockRepository.save(pendingUnblock);
        
        // LOG CRÍTICO: Desbloqueo pendiente guardado exitosamente
        log.info("[OFFLINE_UNBLOCK_SAVED] Desbloqueo pendiente guardado - desbloqueador: {}, desbloqueado: {}", 
                sender, recipient);
    }
}
