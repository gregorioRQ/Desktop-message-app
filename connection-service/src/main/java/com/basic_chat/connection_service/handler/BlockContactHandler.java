package com.basic_chat.connection_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler para solicitudes de bloqueo de contacto (BlockContactRequest).
 * 
 * Este handler procesa las solicitudes de bloqueo enviadas por un cliente.
 * 
 * Flujo de ejecución (dos acciones en paralelo):
 * 1. Envía la solicitud a chat-service para procesar la lógica de ContactBlock:
 *    - Registra el bloqueo en la tabla contact_blocks (permanente)
 *    - Si receptor offline: guarda también en pending_blocks
 * 2. Envía notificación directa al destinatario si está online:
 *    - Online en esta instancia: WebSocket directo
 *    - Online en otra instancia: RabbitMQ a esa instancia
 *    - Offline: no se envía notificación directa (se entrega al conectarse)
 * 
 * El destinatario recibe la notificación para actualizar su DB local,
 * mientras chat-service procesa el registro permanente en contact_blocks.
 */
@Component
@Slf4j
public class BlockContactHandler implements ConnectionWsMessageHandler {

    private final MessageRouterService messageRouterService;

    public BlockContactHandler(MessageRouterService messageRouterService) {
        this.messageRouterService = messageRouterService;
    }

    /**
     * Determina si este handler puede procesar el mensaje recibido.
     * 
     * @param message Mensaje Protobuf recibido del cliente
     * @return true si el mensaje contiene BlockContactRequest
     */
    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasBlockContactRequest();
    }

    /**
     * Procesa la solicitud de bloqueo de contacto.
     * 
     * Este método:
     * 1. Extrae el destinatario del bloqueo del mensaje
     * 2. Envía la solicitud a chat-service para registro permanente (ContactBlock)
     * 3. Envía notificación directa al destinatario si está online
     * 
     * @param sender Username del cliente que envía la solicitud de bloqueo
     * @param message Mensaje Protobuf que contiene BlockContactRequest
     */
    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.BlockContactRequest request = message.getBlockContactRequest();
        String recipient = request.getRecipient();

        // LOG CRÍTICO: Inicio de procesamiento de bloqueo
        log.info("[BLOCK_REQUEST] Remitente: {} → Bloquear a: {}", sender, recipient);

        // Serializar el mensaje completo
        byte[] messageData = message.toByteArray();

        // Enrutar a chat-service para procesamiento y enviar al destinatario
        // Este método hace ambas cosas: 
        // 1) Envía a chat-service para registrar en ContactBlock
        // 2) Envía notificación directa al destinatario si está online
        messageRouterService.routeBlockUnblockToChatService(sender, recipient, messageData);

        // LOG CRÍTICO: Bloqueo enrutado exitosamente
        log.info("[BLOCK_ROUTED] Solicitud de bloqueo de {} hacia {} completada", sender, recipient);
    }
}