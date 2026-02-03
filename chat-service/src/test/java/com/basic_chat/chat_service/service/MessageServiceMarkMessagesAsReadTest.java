package com.basic_chat.chat_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
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

@ExtendWith(MockitoExtension.class)
class MessageServiceMarkMessagesAsReadTest {

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
    void testMarkMessagesAsRead_HappyPath() {
        // Arrange
        String reader = "user2";
        List<String> messageIds = List.of("100", "101");
        
        Message msg1 = new Message();
        msg1.setId(100L);
        msg1.setToUserId(reader);
        msg1.setSeen(false);

        Message msg2 = new Message();
        msg2.setId(101L);
        msg2.setToUserId(reader);
        msg2.setSeen(false);

        when(messageRepository.findAllById(List.of(100L, 101L))).thenReturn(List.of(msg1, msg2));
        when(messageRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<Message> result = messageService.markMessagesAsRead(messageIds, reader);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.get(0).isSeen());
        assertTrue(result.get(1).isSeen());
        
        verify(messageRepository).findAllById(List.of(100L, 101L));
        verify(messageRepository).saveAll(anyList());
    }

    @Test
    void testMarkMessagesAsRead_NullOrEmptyIds() {
        // Act
        List<Message> resultNull = messageService.markMessagesAsRead(null, "user1");
        List<Message> resultEmpty = messageService.markMessagesAsRead(Collections.emptyList(), "user1");

        // Assert
        assertTrue(resultNull.isEmpty());
        assertTrue(resultEmpty.isEmpty());
        verifyNoInteractions(messageRepository);
    }

    @Test
    void testMarkMessagesAsRead_NoMessagesFound() {
        // Arrange
        String reader = "user2";
        List<String> messageIds = List.of("100");
        when(messageRepository.findAllById(List.of(100L))).thenReturn(Collections.emptyList());

        // Act
        List<Message> result = messageService.markMessagesAsRead(messageIds, reader);

        // Assert
        assertTrue(result.isEmpty());
        verify(messageRepository).findAllById(List.of(100L));
        verify(messageRepository, never()).saveAll(anyList());
    }

    @Test
    void testMarkMessagesAsRead_MessagesNotForReaderOrAlreadySeen() {
        // Arrange
        String reader = "user2";
        List<String> messageIds = List.of("100", "101");

        Message msg1 = new Message();
        msg1.setId(100L);
        msg1.setToUserId("otherUser"); // Wrong user
        msg1.setSeen(false);

        Message msg2 = new Message();
        msg2.setId(101L);
        msg2.setToUserId(reader);
        msg2.setSeen(true); // Already seen

        when(messageRepository.findAllById(List.of(100L, 101L))).thenReturn(List.of(msg1, msg2));

        // Act
        List<Message> result = messageService.markMessagesAsRead(messageIds, reader);

        // Assert
        assertTrue(result.isEmpty());
        verify(messageRepository).findAllById(List.of(100L, 101L));
        verify(messageRepository, never()).saveAll(anyList());
    }

    @Test
    void testMarkMessagesAsRead_InvalidIdFormat() {
        // Arrange
        String reader = "user2";
        List<String> messageIds = List.of("100", "invalid");

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            messageService.markMessagesAsRead(messageIds, reader);
        });

        assertEquals("IDs de mensaje inválidos", exception.getMessage());
        verifyNoInteractions(messageRepository);
    }

    @Test
    void testMarkMessagesAsRead_RepositoryException() {
        // Arrange
        String reader = "user2";
        List<String> messageIds = List.of("100");
        when(messageRepository.findAllById(anyList())).thenThrow(new RuntimeException("DB Error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            messageService.markMessagesAsRead(messageIds, reader);
        });

        assertEquals("Error al marcar mensajes como leídos", exception.getMessage());
    }
}