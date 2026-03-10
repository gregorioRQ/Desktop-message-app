package com.basic_chat.connection_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler para solicitudes de eliminación de historial de chat (ClearHistoryRequest).
 * 
 * Este handler procesa las solicitudes de eliminación de todo el historial de chat
 * entre dos usuarios y determina la ruta según el estado del receptor:
 * 
 * - Receptor ONLINE en esta instancia: Enviar solicitud directamente por WebSocket
 * - Receptor ONLINE en otra instancia: Encolar a la cola de la instancia del receptor
 * - Receptor OFFLINE: Encolar a message.offline para que chat-service guarde la solicitud pendiente
 * 
 * El flujo es:
 * Cliente → connection-service (este handler) → MessageRouterService.routeClearHistoryRequest()
 *                                                                                           │
 *                    ┌────────────────────────────────────────┬────────────────────────────────────────┐
 *                    │                                        │                                        │
 *                    ▼                                        ▼                                        ▼
 *         Online (esta instancia)                 Online (otra instancia)                 Offline
 *                    │                                        │                                        │
 *                    ▼                                        ▼                                        ▼
 *         WebSocket directo                 RabbitMQ (message.sent.{instance})    RabbitMQ (message.offline)
 *         al receptor                                                        │
 *                                                                              ▼
 *                                                                    chat-service
 *                                                                    Guarda solicitud
 */
@Component
@Slf4j
public class ClearHistoryHandler implements ConnectionWsMessageHandler {

    private final MessageRouterService messageRouterService;

    public ClearHistoryHandler(MessageRouterService messageRouterService) {
        this.messageRouterService = messageRouterService;
    }

    /**
     * Verifica si este handler puede procesar el mensaje.
     * 
     * @param message Mensaje protobuf recibido
     * @return true si el mensaje contiene un ClearHistoryRequest
     */
    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasClearHistoryRequest();
    }

    /**
     * Procesa la solicitud de eliminación de historial.
     * 
     * Extrae el destinatario del historial a eliminar y lo envía a MessageRouterService
     * que determinará la ruta según el estado del receptor (online/offline).
     * 
     * @param sender Username del usuario que solicitó la eliminación del historial
     * @param message Mensaje protobuf a procesar
     */
    @Override
    public void handle(String sender, MessagesProto.WsMessage message) {
        MessagesProto.ClearHistoryRequest clearRequest = message.getClearHistoryRequest();
        String recipient = clearRequest.getRecipient();
        
        log.info("Procesando solicitud de eliminación de historial entre {} y {}", sender, recipient);
        
        // Serializar el mensaje completo para enviarlo
        byte[] messageData = message.toByteArray();
        
        // Enrutar la solicitud de eliminación de historial
        messageRouterService.routeClearHistoryRequest(sender, recipient, messageData);
    }
}
