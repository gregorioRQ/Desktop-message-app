package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler offline para mensajes de chat.
 * 
 * Cuando el destinatario está offline, guarda el mensaje en la base de datos
 * con seen=false para ser entregado cuando se conecte.
 */
@Component
@Slf4j
public class OfflineChatMessageHandler implements OfflineMessageHandler {

    private final MessageRepository messageRepository;

    public OfflineChatMessageHandler(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasChatMessage();
    }

    @Override
    public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
        MessagesProto.ChatMessage chatMessage = message.getChatMessage();
        
        Message entity = new Message();
        entity.setId(Long.parseLong(chatMessage.getId()));
        entity.setFromUserId(chatMessage.getSender());
        entity.setToUserId(chatMessage.getRecipient());
        entity.setData(chatMessage.toByteArray());
        entity.setSeen(false);
        
        messageRepository.save(entity);
        
        log.info("Mensaje offline guardado - de: {}, para: {}", chatMessage.getSender(), chatMessage.getRecipient());
    }
}
