package com.basic_chat.chat_service.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.basic_chat.chat_service.client.RestClient;
import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.models.MessageDTO;
import com.basic_chat.chat_service.repository.MessageRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final RestClient contactClient;

    public MessageService(MessageRepository messageRepository, RestClient contactClient) {
        this.messageRepository = messageRepository;
        this.contactClient = contactClient;
    }

    public void saveMessage(MessageDTO dto) {
        /*
         * if (contactClient.isUserBlocked(message.getReceiverId(),
         * message.getSenderId())) {
         * throw new
         * IllegalArgumentException("No puedes enviar mensajes a un contacto bloqueado."
         * );
         * }
         */
        Message ms = new Message();
        ms.setSender(dto.getSender());
        ms.setReceiver(dto.getReceiver());
        ms.setContent(dto.getContent());

        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(dto.getCreated_at()),
                ZoneId.systemDefault() // o ZoneId.of("UTC")
        );
        ms.setCreated_at(dateTime);
        ms.setSeen(false);

        messageRepository.save(ms);
    }

    public List<MessageDTO> getUnreadMessages(String username) {

        if (messageRepository.existsByReceiver(username) == false) {
            System.out.println("No hay mensajes para este usuario");
            throw new EntityNotFoundException("Este usuario no existe");
        }

        List<Message> messages = messageRepository.findByReceiverAndIsSeenFalse(username);
        if (messages.isEmpty()) {
            System.out.println("No hay mensajes para este usuario");
            throw new EntityNotFoundException("No hay mensajes para este usuario");
        }
        List<MessageDTO> messageDTOs = new ArrayList<>();
        for (Message m : messages) {
            MessageDTO messageDTO = new MessageDTO();
            messageDTO.setContent(m.getContent());
            messageDTO.setReceiver(m.getReceiver());
            messageDTO.setSender(m.getSender());
            // convierte de LocaDateTime a long
            messageDTO.setCreated_at(m.getCreated_at().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            messageDTO.setImageUrl(m.getImageUrl());
            messageDTOs.add(messageDTO);
        }
        return messageDTOs;
    }

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

    public void deleteMessage(Long messageId, String receiver) {
        /*
         * if (!messageRepository.existMessageByReceiverIdAndMessageId(receiverId,
         * messageId)) {
         * throw new
         * IllegalArgumentException("El mensaje no existe o no pertenece al receptor.");
         * }
         */
        messageRepository.deleteById(messageId);
    }

    // Elimina todos los mensajes entre dos usuarios
    @Transactional
    public void deleteAllMessagesBetweenUsers(String sender, String receiver) {
        messageRepository.deleteAllBySenderAndReceiver(sender, receiver);
    }

    public void deleteAllMessages(String username) {
        messageRepository.deleteAllMessagesByReceiver(username);
    }

}
