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

import com.basic_chat.chat_service.models.PendingDeletion;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.chat_service.repository.PendingDeletionRepository;
import com.basic_chat.chat_service.repository.PendingReadReceiptRepository;
import com.basic_chat.chat_service.validator.MessageValidator;

@ExtendWith(MockitoExtension.class)
class MessageServiceGetAndClearPendingDeletionsTest {

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
    void testGetAndClearPendingDeletions_HappyPath() {
        // Arrange
        String recipient = "user1";
        PendingDeletion pd1 = new PendingDeletion(1L, recipient, "msg1", "user2");
        PendingDeletion pd2 = new PendingDeletion(2L, recipient, "msg2", "user2");
        List<PendingDeletion> pendingList = List.of(pd1, pd2);

        when(pendingDeletionRepository.findByRecipient(recipient)).thenReturn(pendingList);

        // Act
        List<String> result = messageService.getAndClearPendingDeletions(recipient);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.contains("msg1"));
        assertTrue(result.contains("msg2"));

        verify(pendingDeletionRepository).findByRecipient(recipient);
        verify(pendingDeletionRepository).deleteAll(pendingList);
    }

    @Test
    void testGetAndClearPendingDeletions_NullRecipient() {
        // Act
        List<String> result = messageService.getAndClearPendingDeletions(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(pendingDeletionRepository);
    }

    @Test
    void testGetAndClearPendingDeletions_NoPendingDeletions() {
        // Arrange
        String recipient = "user1";
        when(pendingDeletionRepository.findByRecipient(recipient)).thenReturn(Collections.emptyList());

        // Act
        List<String> result = messageService.getAndClearPendingDeletions(recipient);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(pendingDeletionRepository).findByRecipient(recipient);
        // Verifica que no se llame a deleteAll si la lista está vacía
        verify(pendingDeletionRepository, never()).deleteAll(anyList());
    }

    @Test
    void testGetAndClearPendingDeletions_RepositoryException() {
        // Arrange
        String recipient = "user1";
        when(pendingDeletionRepository.findByRecipient(recipient)).thenThrow(new RuntimeException("DB Error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            messageService.getAndClearPendingDeletions(recipient);
        });

        assertEquals("Error al procesar eliminaciones pendientes", exception.getMessage());
        verify(pendingDeletionRepository).findByRecipient(recipient);
    }
}