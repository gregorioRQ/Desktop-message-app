package com.basic_chat.chat_service.consumer;

import com.basic_chat.chat_service.models.RoutedMessageEvent;
import com.basic_chat.chat_service.service.OfflineMessageDispatcher;
import com.basic_chat.proto.MessagesProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de mensajes offline desde RabbitMQ.
 * 
 * Este componente escucha la cola "message.offline" que recibe todos los mensajes
 * dirigidos a usuarios que están desconectados. Anteriormente, solo procesaba
 * mensajes de chat y los guardaba en la base de datos.
 * 
 * Con la nueva arquitectura donde connection-service es el gateway WebSocket,
 * chat-service necesita manejar TODOS los tipos de mensajes que llegan para
 * usuarios offline, no solo mensajes de chat. Esto incluye:
 * - Mensajes de chat (ChatMessage)
 * - Solicitudes de eliminación (DeleteMessageRequest)
 * - Solicitudes de limpieza de historial (ClearHistoryRequest)
 * - Solicitudes de bloqueo (BlockContactRequest)
 * - Solicitudes de desbloqueo (UnblockContactRequest)
 * - Actualizaciones de mensajes leídos (MessagesReadUpdate)
 * - Actualizaciones de identidad de contacto (ContactIdentity)
 * 
 * El patrón de dispatcher permite agregar nuevos tipos de mensajes sin modificar
 * este consumidor, simplemente agregando nuevos handlers que implementen
 * OfflineMessageHandler.
 */
@Component
@Slf4j
public class OfflineMessageConsumer {

    private final OfflineMessageDispatcher offlineMessageDispatcher;

    public OfflineMessageConsumer(OfflineMessageDispatcher offlineMessageDispatcher) {
        this.offlineMessageDispatcher = offlineMessageDispatcher;
    }

    /**
     * Procesa mensajes recibidos de la cola "message.offline".
     * 
     * Este método es invocado por RabbitMQ cuando llega un mensaje a la cola.
     * El mensaje puede ser de cualquier tipo (chat, eliminación, bloqueo, etc.)
     * y el dispatcher se encarga de routing al handler apropiado.
     * 
     * @param event Evento ruteado que contiene el mensaje protobuf y metadatos
     */
    @RabbitListener(queues = "message.offline")
    public void handleOfflineMessage(RoutedMessageEvent event) {
        log.info("Received offline message from {} to {}", event.getSender(), event.getRecipient());

        try {
            MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.parseFrom(event.getContent());
            
            log.info("Procesando mensaje offline tipo: {} para recipient: {}", 
                    wsMessage.getPayloadCase(), event.getRecipient());

            offlineMessageDispatcher.dispatch(wsMessage, event.getRecipient());
            
            log.info("Mensaje offline procesado exitosamente para recipient: {}", event.getRecipient());
        } catch (Exception e) {
            log.error("Error processing offline message from {} to {}: {}",
                    event.getSender(), event.getRecipient(), e.getMessage(), e);
        }
    }
}
