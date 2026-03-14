package com.basic_chat.connection_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler para solicitudes de bloqueo de contacto (BlockContactRequest).
 *
 * Flujo ONLINE:
 * Cliente A → connection-service (este handler) → MessageRouterService
 *   ├── Destinatario online en esta instancia → WebSocket directo
 *   └── Destinatario online en otra instancia → RabbitMQ
 *
 * El destinatario no requiere notificación en tiempo real,
 * el mensaje se procesa para logging y validación.
 */
@Component
@Slf4j
public class BlockContactHandler implements ConnectionWsMessageHandler {

    private final MessageRouterService messageRouterService;

    public BlockContactHandler(MessageRouterService messageRouterService) {
        this.messageRouterService = messageRouterService;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasBlockContactRequest();
    }

    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.BlockContactRequest request = message.getBlockContactRequest();
        String recipient = request.getRecipient();

        // LOG CRÍTICO: Inicio de procesamiento de bloqueo
        log.info("[BLOCK_REQUEST] Remitente: {} → Bloquear a: {}", sender, recipient);

        // Serializar el mensaje completo
        byte[] messageData = message.toByteArray();

        // Enrutar según estado del destinatario (online/offline)
        messageRouterService.routeMessage(sender, recipient, messageData);

        // LOG CRÍTICO: Bloqueo enrutado exitosamente
        log.info("[BLOCK_ROUTED] Solicitud de bloqueo de {} hacia {} enrutada", sender, recipient);
    }
}