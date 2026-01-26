package com.basic_chat.chat_service.validator;

import org.springframework.stereotype.Component;

import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.proto.MessagesProto;

import jakarta.persistence.EntityNotFoundException;

@Component
public class MessageValidator {
    private final MessageRepository messageRepository;

    public MessageValidator(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public Message validateAndGetMessage(Long messageId){
        if (messageId == null) {
            throw new IllegalArgumentException("El ID del mensaje no puede ser nulo");
        }

        return messageRepository.findById(messageId)
                .orElseThrow(() -> new EntityNotFoundException("El mensaje con ID " + messageId + " no existe"));
    }

    public void validateMessageId(Message message) {
        if (message == null) {
            throw new IllegalArgumentException("El mensaje no puede ser nulo");
        }
        if (message.getId() == null) {
            throw new IllegalArgumentException("El ID del mensaje es nulo");
        }
    }

    public void validate(MessagesProto.ChatMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("El mensaje no puede ser nulo");
        }
        if (message.getId() == null || message.getId().isEmpty()) {
            throw new IllegalArgumentException("El ID del mensaje no puede estar vacío");
        }
        if (message.getSender() == null || message.getSender().isEmpty()) {
            throw new IllegalArgumentException("El remitente (sender) no puede estar vacío");
        }
        if (message.getRecipient() == null || message.getRecipient().isEmpty()) {
            throw new IllegalArgumentException("El destinatario (recipient) no puede estar vacío");
        }
        if (message.getContent() == null || message.getContent().isEmpty()) {
            throw new IllegalArgumentException("El contenido del mensaje no puede estar vacío");
        }
    }
}
