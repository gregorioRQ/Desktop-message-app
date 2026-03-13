package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.chat_service.models.PendingReadReceipt;
import com.basic_chat.chat_service.repository.PendingReadReceiptRepository;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler offline para actualizaciones de mensajes leídos (read receipts).
 * 
 * Cuando el destinatario original del mensaje está offline, guarda la actualización
 * de lectura como pendiente para ser notificado cuando se conecte.
 * 
 * Este handler es necesario porque las confirmaciones de lectura (read receipts)
 * deben llegar al remitente original del mensaje, quien puede estar offline en
 * el momento en que el destinatario marca los mensajes como leídos.
 * 
 * La arquitectura actual del sistema (con connection-service como gateway WebSocket)
 * requiere que chat-service almacene estos pendientes y los proporcione cuando el
 * cliente consulte sus mensajes pendientes via REST API.
 */
@Component
@Slf4j
public class OfflineMarkAsReadHandler implements OfflineMessageHandler {

    private final PendingReadReceiptRepository pendingReadReceiptRepository;

    public OfflineMarkAsReadHandler(PendingReadReceiptRepository pendingReadReceiptRepository) {
        this.pendingReadReceiptRepository = pendingReadReceiptRepository;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasMessagesReadUpdate();
    }

    @Override
    @Transactional
    public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
        MessagesProto.MessagesReadUpdate readUpdate = message.getMessagesReadUpdate();
        
        for (String messageId : readUpdate.getMessageIdsList()) {
            PendingReadReceipt pendingReceipt = new PendingReadReceipt();
            pendingReceipt.setMessageId(messageId);
            pendingReceipt.setReceiptRecipient(recipient);
            pendingReceipt.setReader(readUpdate.getReaderUsername());
            
            pendingReadReceiptRepository.save(pendingReceipt);
            
            log.info("Recibo de lectura pendiente guardado - mensaje: {}, leido por: {}, notificar a: {}", 
                    messageId, readUpdate.getReaderUsername(), recipient);
        }
    }
}
