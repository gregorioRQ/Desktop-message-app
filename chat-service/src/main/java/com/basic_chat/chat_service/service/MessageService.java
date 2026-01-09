package com.basic_chat.chat_service.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.chat_service.validator.MessageValidator;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.ChatMessage;
import com.basic_chat.proto.MessagesProto.DeleteMessageRequest;

import jakarta.transaction.Transactional;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final MessageValidator messageValidator;

    public MessageService(MessageRepository messageRepository, MessageValidator messageValidator) {
        this.messageRepository = messageRepository;
        this.messageValidator = messageValidator;
    }

    @Transactional
    public void saveMessage(MessagesProto.ChatMessage message) {
        validateMessage(message);
        try{
            Message mappedMessage = mapProtobufToEntity(message);
            messageRepository.save(mappedMessage);
            System.out.println("Mensaje guardado. ID: " + message.getId());
        }catch(Exception ex){
            throw new RuntimeException("Error al guardar el mensaje", ex);
        }
        
    }

    private void validateMessage(MessagesProto.ChatMessage message){
        if(message == null){
            throw new IllegalArgumentException(
                "El mensaje no puede ser nulo"
            );
        }
        messageValidator.validate(message);
    }

    private Message mapProtobufToEntity(MessagesProto.ChatMessage protoMessage) {
        Message message = new Message();
        System.out.println(protoMessage.getId());
        message.setId(Long.parseLong(protoMessage.getId()));
        message.setFromUserId(protoMessage.getSender());
        message.setToUserId(protoMessage.getRecipient());
        message.setData(protoMessage.toByteArray());
        message.setSeen(false);

        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(System.currentTimeMillis()),
                ZoneId.systemDefault()
        );
        message.setTimestamp(dateTime);
        message.setCreationTime(protoMessage.getTimestamp());
        
        return message;
    }

    @Transactional
    public List<ChatMessage> getUnreadMessages(String username) {

        List<Message> messages = messageRepository.findByToUserIdAndSeenFalse(username);
        if (messages.isEmpty()) {
            System.out.println("No hay mensajes para este usuario");
        }
        
        return messages.stream().map(m -> {
            try{
                return ChatMessage.parseFrom(m.getData());
            }catch(Exception ex){
                throw new RuntimeException("Error al deserializar el mensaje");
            }
            
        }).toList();
    }
    
    public List<ChatMessage> findByToUserId(String toUserId) {
        List<Message> messages = messageRepository.findByToUserId(toUserId);
        return messages.stream()
                .map(message -> {
                    try {
                        return ChatMessage.parseFrom(message.getData());
                    } catch (Exception e) {
                        // Considera un manejo de excepciones más robusto aquí
                        throw new RuntimeException("Error al parsear el mensaje de Protobuf", e);
                    }
                })
                .toList();
    }

    /* 
    @Transactional
    public int markRead(List<Long> messageIds, String receiver) {
        if (messageIds == null || messageIds.isEmpty())
            return 0;

        final int CHUNK = 500;
        int totalUpdated = 0;
        for (int i = 0; i < messageIds.size(); i += CHUNK) {
            int end = Math.min(i + CHUNK, messageIds.size());
            List<Long> sub = messageIds.subList(i, end);
            totalUpdated += messageRepository.markMessagesAsRead(receiver, sub);
        }
        return totalUpdated;
    }
*/
    @Transactional
    public Message deleteMessage(DeleteMessageRequest request) {
        Long messageId = Long.valueOf(request.getMessageId());
        Message message = messageValidator.validateAndGetMessage(messageId);
        messageValidator.validateMessageId(message);
        messageRepository.deleteById(message.getId());
        return message;
    }


/* 
    // Elimina todos los mensajes entre dos usuarios
    @Transactional
    public void deleteAllMessagesBetweenUsers(String sender, String receiver) {
        messageRepository.deleteAllBySenderAndReceiver(sender, receiver);
    }

    public void deleteAllMessages(String username) {
        messageRepository.deleteAllMessagesByReceiver(username);
    }
*/
}
