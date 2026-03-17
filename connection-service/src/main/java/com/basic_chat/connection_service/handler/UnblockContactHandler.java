package com.basic_chat.connection_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler para solicitudes de desbloqueo de contacto (UnblockContactRequest).
 * 
 * Este handler procesa las solicitudes de desbloqueo enviadas por un cliente.
 * 
 * Flujo de ejecución (dos acciones en paralelo):
 * 1. Envía la solicitud a chat-service para procesar la lógica de ContactBlock:
 *    - Elimina el registro de la tabla contact_blocks (permanente)
 *    - Si receptor offline: guarda también en pending_unblocks
 * 2. Envía notificación directa al destinatario si está online:
 *    - Online en esta instancia: WebSocket directo
 *    - Online en otra instancia: RabbitMQ a esa instancia
 *    - Offline: no se envía notificación directa (se entrega al conectarse)
 * 
 * El destinatario recibe la notificación para actualizar su DB local,
 * mientras chat-service procesa la eliminación permanente en contact_blocks.
 */
@Component
@Slf4j
public class UnblockContactHandler implements ConnectionWsMessageHandler {

    private final MessageRouterService messageRouterService;

    public UnblockContactHandler(MessageRouterService messageRouterService) {
        this.messageRouterService = messageRouterService;
    }

    /**
     * Determina si este handler puede procesar el mensaje recibido.
     * 
     * @param message Mensaje Protobuf recibido del cliente
     * @return true si el mensaje contiene UnblockContactRequest
     */
    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasUnblockContactRequest();
    }

    /**
     * Procesa la solicitud de desbloqueo de contacto.
     * 
     * Este método:
     * 1. Extrae el destinatario del desbloqueo del mensaje
     * 2. Envía la solicitud a chat-service para eliminar de ContactBlock
     * 3. Envía notificación directa al destinatario si está online
     * 
     * @param sender Username del cliente que envía la solicitud de desbloqueo
     * @param message Mensaje Protobuf que contiene UnblockContactRequest
     */
    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.UnblockContactRequest request = message.getUnblockContactRequest();
        String recipient = request.getRecipient();

        // LOG CRÍTICO: Inicio de procesamiento de desbloqueo
        log.info("[UNBLOCK_REQUEST] Remitente: {} → Desbloquear a: {}", sender, recipient);

        // Serializar el mensaje completo
        byte[] messageData = message.toByteArray();

        // Enrutar a chat-service para procesamiento y enviar al destinatario
        // Este método hace ambas cosas:
        // 1) Envía a chat-service para eliminar de ContactBlock
        // 2) Envía notificación directa al destinatario si está online
        messageRouterService.routeBlockUnblockToChatService(sender, recipient, messageData);

        // LOG CRÍTICO: Desbloqueo enrutado exitosamente
        log.info("[UNBLOCK_ROUTED] Solicitud de desbloqueo de {} hacia {} completada", sender, recipient);
    }
}