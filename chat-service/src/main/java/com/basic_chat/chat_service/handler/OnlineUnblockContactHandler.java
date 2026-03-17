package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.chat_service.service.BlockService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler online para solicitudes de desbloqueo de contacto.
 * 
 * Este handler procesa las solicitudes de desbloqueo cuando el destinatario está online.
 * A diferencia del handler offline, aquí NO se guarda en pending_unblocks porque
 * el destinatario ya recibe la notificación directamente por WebSocket.
 * 
 * Funcionalidad:
 * - Elimina el registro de la tabla contact_blocks (permanente)
 * - La notificación al destinatario es manejada por connection-service (WebSocket directo)
 * 
 * Este handler es invocado desde ChatMessageConsumer cuando llega un UnblockContactRequest
 * a través de la cola message.sent.{instanceId}.
 */
@Component
@Slf4j
public class OnlineUnblockContactHandler {

    private final BlockService blockService;

    public OnlineUnblockContactHandler(BlockService blockService) {
        this.blockService = blockService;
    }

    /**
     * Determina si este handler puede procesar el mensaje recibido.
     * 
     * @param message Mensaje Protobuf recibido
     * @return true si el mensaje contiene UnblockContactRequest
     */
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasUnblockContactRequest();
    }

    /**
     * Procesa una solicitud de desbloqueo de contacto para un destinatario online.
     * 
     * Este método:
     * 1. Elimina el registro de contact_blocks (tabla permanente)
     * 
     * La notificación al destinatario es manejada por connection-service,
     * quien envía directamente el UnblockContactRequest por WebSocket al cliente B.
     * 
     * @param message Mensaje protobuf que contiene UnblockContactRequest
     */
    public void handleOnline(MessagesProto.WsMessage message) {
        MessagesProto.UnblockContactRequest request = message.getUnblockContactRequest();
        
        // El campo 'blocker' contiene el usuario que envía la solicitud de desbloqueo
        // El campo 'recipient' contiene el usuario que será desbloqueado
        String blocker = request.getBlocker();
        String blockedUser = request.getRecipient();
        
        // LOG CRÍTICO: Inicio de procesamiento de desbloqueo online
        log.info("[ONLINE_UNBLOCK] Bloqueador: {} → Desbloquear a: {}", blocker, blockedUser);

        // Eliminar el registro de contact_blocks (tabla permanente)
        // Esto asegura que el desbloqueo se aplique permanentemente
        // El método unblockUser es idempotente - si no existe, retorna true igualmente
        boolean unblockExecuted = blockService.unblockUser(blocker, blockedUser);

        if (unblockExecuted) {
            log.info("[ONLINE_UNBLOCK_CONTACT] Registro de bloqueo eliminado de contact_blocks - blocker: {}, blocked: {}", 
                    blocker, blockedUser);
        } else {
            log.warn("[ONLINE_UNBLOCK_CONTACT] No se pudo eliminar registro de contact_blocks - blocker: {}, blocked: {}", 
                    blocker, blockedUser);
        }

        // LOG CRÍTICO: Procesamiento de desbloqueo online completado
        log.info("[ONLINE_UNBLOCK_COMPLETE] Procesamiento de desbloqueo online completado - blocker: {}, blocked: {}", 
                blocker, blockedUser);
    }
}
