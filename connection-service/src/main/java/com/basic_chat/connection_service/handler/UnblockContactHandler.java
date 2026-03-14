package com.basic_chat.connection_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler para solicitudes de desbloqueo de contacto (UnblockContactRequest).
 *
 * Flujo ONLINE:
 * Cliente A → connection-service (este handler) → MessageRouterService
 *   ├── Destinatario online en esta instancia → WebSocket directo
 *   └── Destinatario online en otra instancia → RabbitMQ
 */
@Component
@Slf4j
public class UnblockContactHandler implements ConnectionWsMessageHandler {

    private final MessageRouterService messageRouterService;

    public UnblockContactHandler(MessageRouterService messageRouterService) {
        this.messageRouterService = messageRouterService;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasUnblockContactRequest();
    }

    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.UnblockContactRequest request = message.getUnblockContactRequest();
        String recipient = request.getRecipient();

        // LOG CRÍTICO: Inicio de procesamiento de desbloqueo
        log.info("[UNBLOCK_REQUEST] Remitente: {} → Desbloquear a: {}", sender, recipient);

        // Serializar el mensaje completo
        byte[] messageData = message.toByteArray();

        // Enrutar según estado del destinatario
        messageRouterService.routeMessage(sender, recipient, messageData);

        // LOG CRÍTICO: Desbloqueo enrutado exitosamente
        log.info("[UNBLOCK_ROUTED] Solicitud de desbloqueo de {} hacia {} enrutada", sender, recipient);
    }
}