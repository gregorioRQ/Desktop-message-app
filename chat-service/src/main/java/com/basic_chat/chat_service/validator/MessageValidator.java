package com.basic_chat.chat_service.validator;

import org.springframework.stereotype.Component;

import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.repository.MessageRepository;

import jakarta.persistence.EntityNotFoundException;

@Component
public class MessageValidator {
    private final MessageRepository messageRepository;

    public MessageValidator(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    public Message validateAndGetMessage(Long messageId) {
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
}
