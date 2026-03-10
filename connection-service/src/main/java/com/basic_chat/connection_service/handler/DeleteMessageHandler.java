package com.basic_chat.connection_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.connection_service.service.PendingMessagesService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler para solicitudes de eliminación de mensajes (DeleteMessageRequest).
 * 
 * Este handler procesa las solicitudes de eliminación de mensajes enviadas por el cliente
 * y determina la ruta según el estado del receptor:
 * 
 * - Receptor ONLINE en esta instancia: Enviar notificación directamente por WebSocket
 * - Receptor ONLINE en otra instancia: Encolar a la cola de la instancia del receptor
 * - Receptor OFFLINE: Encolar a message.offline para que chat-service guarde la eliminación pendiente
 * 
 * El flujo es:
 * Cliente → connection-service (este handler) → Lógica online/offline
 *                                                            │
 *                    ┌────────────────────────────────────────┼────────────────────────────────────────┐
 *                    │                                        │                                        │
 *                    ▼                                        ▼                                        ▼
 *         Online (esta instancia)                 Online (otra instancia)                 Offline
 *                    │                                        │                                        │
 *                    ▼                                        ▼                                        ▼
 *         WebSocket directo                 RabbitMQ (message.sent.{instance})    RabbitMQ (message.offline)
 *         al receptor                                                        │
 *                                                                              ▼
 *                                                                   chat-service
 *                                                                   Guarda PendingDeletion
 */
@Component
@Slf4j
public class DeleteMessageHandler implements ConnectionWsMessageHandler {

    private final MessageRouterService messageRouterService;
    private final PendingMessagesService pendingMessagesService;

    public DeleteMessageHandler(
            MessageRouterService messageRouterService,
            PendingMessagesService pendingMessagesService) {
        this.messageRouterService = messageRouterService;
        this.pendingMessagesService = pendingMessagesService;
    }

    /**
     * Verifica si este handler puede procesar el mensaje.
     * 
     * @param message Mensaje protobuf recibido
     * @return true si el mensaje contiene un DeleteMessageRequest
     */
    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasDeleteMessageRequest();
    }

    /**
     * Procesa la solicitud de eliminación de mensaje.
     * 
     * Pasos:
     * 1. Extraer messageId y sender del DeleteMessageRequest
     * 2. Obtener el mensaje original de chat-service para conocer el destinatario
     * 3. Consultar Redis para determinar si el destinatario está online
     * 4. Si online: enviar notificación (misma instancia → WebSocket, otra instancia → RabbitMQ)
     * 5. Si offline: encolar a message.offline para que chat-service guarde PendingDeletion
     * 
     * @param sender Username del usuario que solicitó la eliminación
     * @param message Mensaje protobuf a procesar
     */
    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.DeleteMessageRequest deleteRequest = message.getDeleteMessageRequest();
        String messageId = deleteRequest.getMessageId();
        
        log.info("Procesando solicitud de eliminación de mensaje {} por usuario {}", messageId, sender);
        
        try {
            // Obtener el mensaje original de chat-service para saber quién es el destinatario
            String recipient = pendingMessagesService.getMessageRecipient(messageId);
            
            if (recipient == null) {
                log.error("No se pudo obtener el destinatario del mensaje {}. El mensaje podría no existir.", messageId);
                return;
            }
            
            log.info("Destinatario del mensaje {} es: {}", messageId, recipient);
            
            // Enrutar según el estado del destinatario
            byte[] messageData = message.toByteArray();
            messageRouterService.routeDeletionNotification(sender, recipient, messageId, messageData);
            
        } catch (Exception e) {
            log.error("Error al procesar solicitud de eliminación de mensaje {}: {}", messageId, e.getMessage(), e);
        }
    }
}
