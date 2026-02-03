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

import com.basic_chat.chat_service.models.PendingReadReceipt;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.chat_service.repository.PendingDeletionRepository;
import com.basic_chat.chat_service.repository.PendingReadReceiptRepository;
import com.basic_chat.chat_service.validator.MessageValidator;

@ExtendWith(MockitoExtension.class)
class MessageServicePendingReadReceiptsTest {

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

    // --- Tests para savePendingReadReceipts ---

    @Test
    void testSavePendingReadReceipts_HappyPath() {
        // Arrange
        String recipient = "user1";
        String reader = "user2";
        List<String> messageIds = List.of("msg1", "msg2");

        // Act
        messageService.savePendingReadReceipts(recipient, messageIds, reader);

        // Assert
        verify(pendingReadReceiptRepository).saveAll(anyList());
    }

    @Test
    void testSavePendingReadReceipts_NullRecipient() {
        // Act
        messageService.savePendingReadReceipts(null, List.of("msg1"), "user2");

        // Assert
        verifyNoInteractions(pendingReadReceiptRepository);
    }

    @Test
    void testSavePendingReadReceipts_EmptyMessageIds() {
        // Act
        messageService.savePendingReadReceipts("user1", Collections.emptyList(), "user2");

        // Assert
        verifyNoInteractions(pendingReadReceiptRepository);
    }

    @Test
    void testSavePendingReadReceipts_NullReader() {
        // Act
        messageService.savePendingReadReceipts("user1", List.of("msg1"), null);

        // Assert
        verifyNoInteractions(pendingReadReceiptRepository);
    }

    @Test
    void testSavePendingReadReceipts_RepositoryException() {
        // Arrange
        doThrow(new RuntimeException("DB Error")).when(pendingReadReceiptRepository).saveAll(anyList());

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            messageService.savePendingReadReceipts("user1", List.of("msg1"), "user2");
        });

        assertEquals("Error procesando solicitudes de lectura", exception.getMessage());
    }

    // --- Tests para getAndClearPendingReadReceipts ---

    @Test
    void testGetAndClearPendingReadReceipts_HappyPath() {
        // Arrange
        String recipient = "user1";
        PendingReadReceipt pr1 = new PendingReadReceipt(1L, "msg1", recipient, "user2");
        List<PendingReadReceipt> pendingList = List.of(pr1);

        when(pendingReadReceiptRepository.findByReceiptRecipient(recipient)).thenReturn(pendingList);

        // Act
        List<PendingReadReceipt> result = messageService.getAndClearPendingReadReceipts(recipient);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        verify(pendingReadReceiptRepository).deleteAll(pendingList);
    }

    @Test
    void testGetAndClearPendingReadReceipts_NullRecipient() {
        // Act
        List<PendingReadReceipt> result = messageService.getAndClearPendingReadReceipts(null);

        // Assert
        assertTrue(result.isEmpty());
        verifyNoInteractions(pendingReadReceiptRepository);
    }

    @Test
    void testGetAndClearPendingReadReceipts_NoPendingReceipts() {
        // Arrange
        String recipient = "user1";
        when(pendingReadReceiptRepository.findByReceiptRecipient(recipient)).thenReturn(Collections.emptyList());

        // Act
        List<PendingReadReceipt> result = messageService.getAndClearPendingReadReceipts(recipient);

        // Assert
        assertTrue(result.isEmpty());
        verify(pendingReadReceiptRepository, never()).deleteAll(anyList());
    }

    @Test
    void testGetAndClearPendingReadReceipts_RepositoryException() {
        // Arrange
        String recipient = "user1";
        when(pendingReadReceiptRepository.findByReceiptRecipient(recipient)).thenThrow(new RuntimeException("DB Error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            messageService.getAndClearPendingReadReceipts(recipient);
        });

        assertEquals("Error al intentar eliminar las confirmaciones de lectura", exception.getMessage());
    }
}