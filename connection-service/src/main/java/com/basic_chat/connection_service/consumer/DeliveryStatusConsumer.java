package com.basic_chat.connection_service.consumer;

import com.basic_chat.connection_service.models.DeliveryStatusEvent;
import com.basic_chat.connection_service.service.SessionRegistryService;
import com.basic_chat.proto.MessagesProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de eventos de estado de entrega desde RabbitMQ.
 * 
 * Este componente escucha la cola "message.delivery" donde chat-service
 * publica los eventos de estado de los mensajes.
 * 
 * Tipos de eventos manejados:
 * - DELIVERED: Mensaje entregado al destinatario
 * - READ: Mensaje marcado como leído por el destinatario
 * - BLOCKED: Mensaje bloqueado porque el remitente fue bloqueado
 * - MESSAGE_DELETED: Notificación de eliminación de mensaje por otro usuario
 * 
 * Responsabilidad: Reenviar estos eventos al cliente destinatario via WebSocket.
 */
@Component
@Slf4j
public class DeliveryStatusConsumer {

    private final SessionRegistryService sessionRegistryService;

    public DeliveryStatusConsumer(SessionRegistryService sessionRegistryService) {
        this.sessionRegistryService = sessionRegistryService;
    }

    /**
     * Procesa un evento de estado de entrega recibido desde chat-service via RabbitMQ.
     * 
     * Este método determina el tipo de evento y lo reenvía al cliente destinatario:
     * - DELIVERED, READ, BLOCKED: Datos del mensaje original
     * - MESSAGE_DELETED: MessageDeletedNotification (WsMessage con MessageDeletedNotification)
     * 
     * El flujo es:
     * chat-service → RabbitMQ (message.delivery) → connection-service (este método) → cliente WebSocket
     * 
     * @param event Evento de estado de entrega contendo tipo, destinatario y datos
     */
    @RabbitListener(queues = "message.delivery")
    public void handleDeliveryStatus(DeliveryStatusEvent event) {
        log.info("Received delivery status: type={}, messageId={}, recipient={}",
                event.getType(), event.getMessageId(), event.getRecipient());

        String recipient = event.getRecipient();
        
        // Manejar específicamente el tipo MESSAGE_DELETED
        if ("MESSAGE_DELETED".equals(event.getType())) {
            handleMessageDeletedNotification(event);
            return;
        }
        
        // Para los demás tipos (DELIVERED, READ, BLOCKED), enviar los datos originales
        sessionRegistryService.sendToUserByUsername(recipient, event.getData());
    }

    /**
     * Procesa una notificación de eliminación de mensaje.
     * 
     * Cuando otro usuario elimina un mensaje "para todos", chat-service envía
     * esta notificación. Los datos contienen un WsMessage con MessageDeletedNotification.
     * 
     * El cliente distingue este tipo de mensaje de un DeleteMessageRequest porque:
     * - DeleteMessageRequest: Lo envía el propio cliente al servidor
     * - MessageDeletedNotification: Lo envía el servidor cuando otro usuario eliminó un mensaje
     * 
     * @param event Evento de eliminación contendo los datos de la notificación
     */
    private void handleMessageDeletedNotification(DeliveryStatusEvent event) {
        String recipient = event.getRecipient();
        
        try {
            // Los datos contienen un WsMessage con MessageDeletedNotification
            byte[] data = event.getData();
            
            if (data == null || data.length == 0) {
                log.warn("MESSAGE_DELETED received but data is empty for recipient: {}", recipient);
                return;
            }
            
            // Parsear para verificar que es un mensaje válido
            MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.parseFrom(data);
            
            if (wsMessage.hasMessageDeleteNotification()) {
                MessagesProto.MessageDeletedNotification notification = wsMessage.getMessageDeleteNotification();
                log.info("Procesando MESSAGE_DELETED para destinatario: {}, mensajeID: {}, eliminadoPor: {}", 
                        recipient, notification.getMessageId(), notification.getDeletedBy());
                
                // Enviar la notificación al cliente via WebSocket
                sessionRegistryService.sendToUserByUsername(recipient, data);
                log.info("MessageDeletedNotification enviada a {} via WebSocket", recipient);
            } else {
                log.warn("MESSAGE_DELETED received but WsMessage no tiene MessageDeleteNotification");
            }
            
        } catch (Exception e) {
            log.error("Error procesando MESSAGE_DELETED para {}: {}", recipient, e.getMessage(), e);
        }
    }
}
