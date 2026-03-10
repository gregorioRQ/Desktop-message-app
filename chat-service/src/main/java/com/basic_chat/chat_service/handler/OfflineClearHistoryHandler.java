package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.chat_service.models.PendingClearHistory;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.chat_service.repository.PendingClearHistoryRepository;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler offline para solicitudes de eliminación de historial.
 * 
 * Este handler procesa las solicitudes de eliminación de historial de chat
 * cuando el destinatario está offline.
 * 
 * Flujo:
 * 1. El usuario A elimina historial con usuario B (ClearHistoryRequest)
 * 2. Si B está offline: este handler procesa la solicitud
 * 3. Se eliminan los mensajes de la BD del servidor entre A y B
 * 4. Se guarda una notificación pendiente para que B elimine su copia local
 *    cuando se conecte
 * 
 * Diferencia con DeleteMessageHandler:
 * - DeleteMessageHandler: Elimina UN mensaje específico
 * - Este handler: Elimina TODOS los mensajes entre dos usuarios
 */
@Component
@Slf4j
public class OfflineClearHistoryHandler implements OfflineMessageHandler {

    private final PendingClearHistoryRepository pendingClearHistoryRepository;
    private final MessageRepository messageRepository;

    public OfflineClearHistoryHandler(PendingClearHistoryRepository pendingClearHistoryRepository,
                                     MessageRepository messageRepository) {
        this.pendingClearHistoryRepository = pendingClearHistoryRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasClearHistoryRequest();
    }

    /**
     * Procesa una solicitud de eliminación de historial para un destinatario offline.
     * 
     * Acciones:
     * 1. Elimina todos los mensajes de la BD entre el remitente y el destinatario
     * 2. Guarda una notificación pendiente para que el destinatario elimine su copia local
     * 
     * @param message Mensaje protobuf con ClearHistoryRequest
     * @param recipient Username del destinatario (que está offline)
     * @throws Exception si ocurre un error al procesar
     */
    @Override
    public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
        MessagesProto.ClearHistoryRequest request = message.getClearHistoryRequest();
        
        String sender = request.getSender();
        log.info("Procesando solicitud de eliminación de historial offline - De: {}, Para: {}", sender, recipient);
        
        // 1. Eliminar mensajes de la BD del servidor entre los dos usuarios
        // Esto elimina tanto mensajes enviados por A a B como los enviados por B a A
        try {
            messageRepository.deleteAllByFromUserIdAndToUserId(sender, recipient);
            log.info("Mensajes eliminados de la BD entre {} y {}", sender, recipient);
            
            // También eliminar mensajes en la otra dirección (B -> A)
            messageRepository.deleteAllByFromUserIdAndToUserId(recipient, sender);
            log.info("Mensajes eliminados de la BD entre {} y {} (dirección inversa)", recipient, sender);
            
        } catch (Exception e) {
            log.error("Error al eliminar mensajes de la BD: {}", e.getMessage(), e);
            // Continuamos aunque falle la eliminación en BD - guardamos la notificación pendiente
            // para que al menos el cliente elimine su copia local
        }
        
        // 2. Guardar notificación pendiente para que el destinatario elimine su copia local
        // El cliente recibirá esta notificación cuando se conecte
        PendingClearHistory pendingClearHistory = new PendingClearHistory();
        pendingClearHistory.setSender(sender);
        pendingClearHistory.setRecipient(recipient);
        pendingClearHistory.setTimestamp(System.currentTimeMillis());
        
        pendingClearHistoryRepository.save(pendingClearHistory);
        
        log.info("Notificación de eliminación de historial guardada - De: {}, Para: {}", sender, recipient);
    }
}
