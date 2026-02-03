package com.basic_chat.chat_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.chat_service.repository.PendingDeletionRepository;
import com.basic_chat.chat_service.repository.PendingReadReceiptRepository;
import com.basic_chat.chat_service.validator.MessageValidator;
import com.basic_chat.proto.MessagesProto.ChatMessage;

@ExtendWith(MockitoExtension.class)
class MessageServiceGetUnreadMessagesTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private PendingDeletionRepository pendingDeletionRepository;

    @Mock
    private PendingReadReceiptRepository pendingReadReceiptRepository;

    @Mock
    private MessageValidator messageValidator;

    @InjectMocks
    private MessageService messageService;

    @Test
    void testGetUnreadMessages_HappyPath() {
        // Arrange
        String username = "user1";
        ChatMessage protoMsg = ChatMessage.newBuilder()
                .setId("100")
                .setSender("sender1")
                .setRecipient(username)
                .setContent("Hello World")
                .build();

        Message messageEntity = new Message();
        messageEntity.setId(100L);
        messageEntity.setToUserId(username);
        messageEntity.setSeen(false);
        messageEntity.setData(protoMsg.toByteArray());

        when(messageRepository.findByToUserIdAndSeenFalse(username))
                .thenReturn(List.of(messageEntity));

        // Act
        List<ChatMessage> result = messageService.getUnreadMessages(username);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("100", result.get(0).getId());
        assertEquals("Hello World", result.get(0).getContent());
        
        verify(messageRepository).findByToUserIdAndSeenFalse(username);
    }

    @Test
    void testGetUnreadMessages_NullUsername() {
        // Act
        List<ChatMessage> result = messageService.getUnreadMessages(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(messageRepository);
    }

    @Test
    void testGetUnreadMessages_NoMessagesFound() {
        // Arrange
        String username = "user1";
        when(messageRepository.findByToUserIdAndSeenFalse(username))
                .thenReturn(Collections.emptyList());

        // Act
        List<ChatMessage> result = messageService.getUnreadMessages(username);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(messageRepository).findByToUserIdAndSeenFalse(username);
    }

    @Test
    void testGetUnreadMessages_RepositoryThrowsException() {
        // Arrange
        String username = "user1";
        when(messageRepository.findByToUserIdAndSeenFalse(username))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            messageService.getUnreadMessages(username);
        });

        assertEquals("No se pudo procesar la lista de mensajes sin leer", exception.getMessage());
        verify(messageRepository).findByToUserIdAndSeenFalse(username);
    }

    @Test
    void testGetUnreadMessages_ProtobufParsingError() {
        // Arrange
        String username = "user1";
        Message messageEntity = new Message();
        messageEntity.setId(100L);
        messageEntity.setData(new byte[]{1, 2, 3, 4}); // Invalid protobuf data

        when(messageRepository.findByToUserIdAndSeenFalse(username))
                .thenReturn(List.of(messageEntity));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            messageService.getUnreadMessages(username);
        });

        assertEquals("No se pudo procesar la lista de mensajes sin leer", exception.getMessage());
    }
}