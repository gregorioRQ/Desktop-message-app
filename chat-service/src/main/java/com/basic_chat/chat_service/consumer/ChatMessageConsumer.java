package com.basic_chat.chat_service.consumer;

import com.basic_chat.chat_service.handler.OnlineBlockContactHandler;
import com.basic_chat.chat_service.handler.OnlineUnblockContactHandler;
import com.basic_chat.chat_service.models.RoutedMessageEvent;
import com.basic_chat.chat_service.service.DeliveryService;
import com.basic_chat.proto.MessagesProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Consumidor de mensajes en cola RabbitMQ para mensajes destinados a esta instancia.
 * 
 * Este consumidor procesa mensajes que llegan a través de la cola message.sent.{instanceId},
 * que contiene mensajes destinados a usuarios conectados en esta instancia específica.
 * 
 * Tipos de mensajes procesados:
 * - ChatMessage: Mensajes de chat normales (usa DeliveryService)
 * - BlockContactRequest: Solicitudes de bloqueo (usa OnlineBlockContactHandler)
 * - UnblockContactRequest: Solicitudes de desbloqueo (usa OnlineUnblockContactHandler)
 * 
 * La diferencia con OfflineMessageConsumer es que este maneja mensajes para usuarios
 * que están ONLINE en esta instancia, mientras que offline maneja usuarios OFFLINE.
 */
@Component
@Slf4j
public class ChatMessageConsumer {

    private final DeliveryService deliveryService;
    private final OnlineBlockContactHandler onlineBlockContactHandler;
    private final OnlineUnblockContactHandler onlineUnblockContactHandler;
    private final String instanceId;

    public ChatMessageConsumer(
            DeliveryService deliveryService,
            OnlineBlockContactHandler onlineBlockContactHandler,
            OnlineUnblockContactHandler onlineUnblockContactHandler,
            @Value("${chat.service.instance.id:instance-1}") String instanceId) {
        this.deliveryService = deliveryService;
        this.onlineBlockContactHandler = onlineBlockContactHandler;
        this.onlineUnblockContactHandler = onlineUnblockContactHandler;
        this.instanceId = instanceId;
    }

    /**
     * Procesa mensajes recibidos de la cola RabbitMQ para esta instancia.
     * 
     * Este método determina el tipo de mensaje y lo enruta al handler apropiado:
     * - ChatMessage: Entrega el mensaje al destinatario
     * - BlockContactRequest: Registra el bloqueo en contact_blocks
     * - UnblockContactRequest: Elimina el registro de contact_blocks
     * 
     * @param event Evento de mensaje enrutado con remitente, destinatario y contenido
     */
    @RabbitListener(queues = "message.sent." + "${chat.service.instance.id:instance-1}")
    public void handleRoutedMessage(RoutedMessageEvent event) {
        log.debug("Received message from {} to {} in instance {}",
                event.getSender(), event.getRecipient(), instanceId);

        try {
            MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.parseFrom(event.getContent());

            if (wsMessage.hasChatMessage()) {
                // Procesar mensaje de chat normal
                MessagesProto.ChatMessage chatMessage = wsMessage.getChatMessage();
                
                log.debug("Processing chat message ID: {}", chatMessage.getId());
                
                deliveryService.processMessage(wsMessage, chatMessage);
            } else if (wsMessage.hasBlockContactRequest()) {
                // Procesar solicitud de bloqueo para usuario online
                // El bloqueo se registra en contact_blocks
                // La notificación al destinatario es manejada por connection-service
                log.debug("Processing block contact request from {} to {}", 
                        event.getSender(), event.getRecipient());
                
                onlineBlockContactHandler.handleOnline(wsMessage);
            } else if (wsMessage.hasUnblockContactRequest()) {
                // Procesar solicitud de desbloqueo para usuario online
                // El desbloqueo se registra en contact_blocks
                // La notificación al destinatario es manejada por connection-service
                log.debug("Processing unblock contact request from {} to {}", 
                        event.getSender(), event.getRecipient());
                
                onlineUnblockContactHandler.handleOnline(wsMessage);
            } else {
                log.warn("Unhandled message type received: {}", wsMessage.getPayloadCase());
            }
        } catch (Exception e) {
            log.error("Error processing routed message from {} to {}: {}",
                    event.getSender(), event.getRecipient(), e.getMessage());
        }
    }
}
