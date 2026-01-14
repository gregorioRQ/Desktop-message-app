package com.basic_chat.chat_service.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.models.PendingDeletion;
import com.basic_chat.chat_service.models.PendingReadReceipt;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.chat_service.repository.PendingDeletionRepository;
import com.basic_chat.chat_service.repository.PendingReadReceiptRepository;
import com.basic_chat.chat_service.validator.MessageValidator;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.ChatMessage;
import com.basic_chat.proto.MessagesProto.DeleteMessageRequest;

import jakarta.transaction.Transactional;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final PendingDeletionRepository pendingDeletionRepository;
    private final PendingReadReceiptRepository pendingReadReceiptRepository;
    private final MessageValidator messageValidator;

    public MessageService(MessageRepository messageRepository, PendingDeletionRepository pendingDeletionRepository, PendingReadReceiptRepository pendingReadReceiptRepository, MessageValidator messageValidator) {
        this.messageRepository = messageRepository;
        this.pendingDeletionRepository = pendingDeletionRepository;
        this.pendingReadReceiptRepository = pendingReadReceiptRepository;
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

    @Transactional
    public List<Message> markMessagesAsRead(List<String> messageIds, String reader) {
        if (messageIds == null || messageIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> ids = messageIds.stream().map(Long::valueOf).toList();
        List<Message> messages = messageRepository.findAllById(ids);
        List<Message> updatedMessages = new ArrayList<>();

        for (Message m : messages) {
            if (m.getToUserId().equals(reader) && !m.isSeen()) {
                m.setSeen(true);
                updatedMessages.add(m);
            }
        }
        messageRepository.saveAll(updatedMessages);
        return updatedMessages;
    }

    @Transactional
    public Message deleteMessage(DeleteMessageRequest request) {
        Long messageId = Long.valueOf(request.getMessageId());
        Message message = messageValidator.validateAndGetMessage(messageId);
        messageValidator.validateMessageId(message);
        messageRepository.deleteById(message.getId());
        return message;
    }


 
    // Elimina todos los mensajes entre dos usuarios
    @Transactional
    public void deleteAllMessagesBetweenUsers(String sender, String receiver) {
        messageRepository.deleteAllByFromUserIdAndToUserId(sender, receiver);
    }
/*
    public void deleteAllMessages(String username) {
        messageRepository.deleteAllMessagesByReceiver(username);
    }
*/

    @Transactional
    public void savePendingDeletion(String recipient, String messageId) {
        PendingDeletion pd = new PendingDeletion(null, recipient, messageId);
        pendingDeletionRepository.save(pd);
    }

    @Transactional
    public List<String> getAndClearPendingDeletions(String recipient) {
        List<PendingDeletion> pending = pendingDeletionRepository.findByRecipient(recipient);
        List<String> ids = pending.stream().map(PendingDeletion::getMessageId).toList();
        
        if (!pending.isEmpty()) {
            pendingDeletionRepository.deleteAll(pending);
        }
        return ids;
    }

    @Transactional
    public void savePendingReadReceipts(String receiptRecipient, List<String> messageIds, String reader) {
        List<PendingReadReceipt> receipts = messageIds.stream()
                .map(msgId -> new PendingReadReceipt(null, msgId, receiptRecipient, reader))
                .toList();
        pendingReadReceiptRepository.saveAll(receipts);
    }

    @Transactional
    public List<PendingReadReceipt> getAndClearPendingReadReceipts(String receiptRecipient) {
        List<PendingReadReceipt> pending = pendingReadReceiptRepository.findByReceiptRecipient(receiptRecipient);
        
        if (!pending.isEmpty()) {
            pendingReadReceiptRepository.deleteAll(pending);
        }
        return pending;
    }
}
