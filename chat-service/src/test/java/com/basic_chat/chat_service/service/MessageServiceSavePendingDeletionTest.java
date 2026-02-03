package com.basic_chat.chat_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

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
class MessageServiceSavePendingDeletionTest {

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
    void testSavePendingDeletion_HappyPath() {
        // Arrange
        String recipient = "user1";
        String messageId = "msg123";
        
        when(pendingDeletionRepository.save(any(PendingDeletion.class))).thenAnswer(i -> i.getArguments()[0]);

        // Act
        messageService.savePendingDeletion(recipient, messageId);

        // Assert
        verify(pendingDeletionRepository).save(argThat(pd -> 
            pd.getRecipient().equals(recipient) && 
            pd.getMessageId().equals(messageId)
        ));
    }

    @Test
    void testSavePendingDeletion_NullRecipient() {
        // Act
        messageService.savePendingDeletion(null, "msg123");

        // Assert
        verifyNoInteractions(pendingDeletionRepository);
    }

    @Test
    void testSavePendingDeletion_NullMessageId() {
        // Act
        messageService.savePendingDeletion("user1", null);

        // Assert
        verifyNoInteractions(pendingDeletionRepository);
    }

    @Test
    void testSavePendingDeletion_BothNull() {
        // Act
        messageService.savePendingDeletion(null, null);

        // Assert
        verifyNoInteractions(pendingDeletionRepository);
    }

    @Test
    void testSavePendingDeletion_RepositoryException() {
        // Arrange
        String recipient = "user1";
        String messageId = "msg123";
        when(pendingDeletionRepository.save(any(PendingDeletion.class))).thenThrow(new RuntimeException("DB Error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            messageService.savePendingDeletion(recipient, messageId);
        });

        assertEquals("Error al guardar la solicitud de eliminacion pendiente", exception.getMessage());
        verify(pendingDeletionRepository).save(any(PendingDeletion.class));
    }
}