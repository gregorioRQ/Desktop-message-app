package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.chat_service.service.BlockService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler online para solicitudes de bloqueo de contacto.
 * 
 * Este handler procesa las solicitudes de bloqueo cuando el destinatario está online.
 * A diferencia del handler offline, aquí NO se guarda en pending_blocks porque
 * el destinatario ya recibe la notificación directamente por WebSocket.
 * 
 * Funcionalidad:
 * - Registra el bloqueo en la tabla contact_blocks (permanente)
 * - La notificación al destinatario es manejada por connection-service (WebSocket directo)
 * 
 * Este handler es invocado desde ChatMessageConsumer cuando llega un BlockContactRequest
 * a través de la cola message.sent.{instanceId}.
 */
@Component
@Slf4j
public class OnlineBlockContactHandler {

    private final BlockService blockService;

    public OnlineBlockContactHandler(BlockService blockService) {
        this.blockService = blockService;
    }

    /**
     * Determina si este handler puede procesar el mensaje recibido.
     * 
     * @param message Mensaje Protobuf recibido
     * @return true si el mensaje contiene BlockContactRequest
     */
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasBlockContactRequest();
    }

    /**
     * Procesa una solicitud de bloqueo de contacto para un destinatario online.
     * 
     * Este método:
     * 1. Registra el bloqueo en contact_blocks (tabla permanente)
     * 
     * La notificación al destinatario es manejada por connection-service,
     * quien envía directamente el BlockContactRequest por WebSocket al cliente B.
     * 
     * @param message Mensaje protobuf que contiene BlockContactRequest
     */
    public void handleOnline(MessagesProto.WsMessage message) {
        MessagesProto.BlockContactRequest request = message.getBlockContactRequest();
        
        // El campo 'blocker' contiene el usuario que envía la solicitud de bloqueo
        // El campo 'recipient' contiene el usuario que será bloqueado
        String blocker = request.getBlocker();
        String blockedUser = request.getRecipient();

        // LOG CRÍTICO: Inicio de procesamiento de bloqueo online
        log.info("[ONLINE_BLOCK] Bloqueador: {} → Bloquear a: {}", blocker, blockedUser);

        // Registrar el bloqueo en contact_blocks (tabla permanente)
        // Esto asegura que el bloqueo persista y sea verificado al enviar mensajes
        // El método blockUser es idempotente - si ya existe, retorna true sin duplicar
        boolean blockRegistered = blockService.blockUser(blocker, blockedUser);

        if (blockRegistered) {
            log.info("[ONLINE_BLOCK_CONTACT] Registro de bloqueo creado en contact_blocks - blocker: {}, blocked: {}", 
                    blocker, blockedUser);
        } else {
            log.warn("[ONLINE_BLOCK_CONTACT] No se pudo registrar bloqueo en contact_blocks - blocker: {}, blocked: {}", 
                    blocker, blockedUser);
        }

        // LOG CRÍTICO: Procesamiento de bloqueo online completado
        log.info("[ONLINE_BLOCK_COMPLETE] Procesamiento de bloqueo online completado - blocker: {}, blocked: {}", 
                blocker, blockedUser);
    }
}
