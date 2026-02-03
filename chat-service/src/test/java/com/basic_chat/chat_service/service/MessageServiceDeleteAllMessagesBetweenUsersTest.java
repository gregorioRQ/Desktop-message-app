package com.basic_chat.chat_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.chat_service.repository.PendingDeletionRepository;
import com.basic_chat.chat_service.repository.PendingReadReceiptRepository;
import com.basic_chat.chat_service.validator.MessageValidator;

@ExtendWith(MockitoExtension.class)
class MessageServiceDeleteAllMessagesBetweenUsersTest {

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
    void testDeleteAllMessagesBetweenUsers_HappyPath() {
        // Arrange
        String sender = "user1";
        String receiver = "user2";

        // Act
        messageService.deleteAllMessagesBetweenUsers(sender, receiver);

        // Assert
        verify(messageRepository).deleteAllByFromUserIdAndToUserId(sender, receiver);
    }

    @Test
    void testDeleteAllMessagesBetweenUsers_NullSender() {
        // Arrange
        String sender = null;
        String receiver = "user2";

        // Act
        messageService.deleteAllMessagesBetweenUsers(sender, receiver);

        // Assert
        verifyNoInteractions(messageRepository);
    }

    @Test
    void testDeleteAllMessagesBetweenUsers_NullReceiver() {
        // Arrange
        String sender = "user1";
        String receiver = null;

        // Act
        messageService.deleteAllMessagesBetweenUsers(sender, receiver);

        // Assert
        verifyNoInteractions(messageRepository);
    }

    @Test
    void testDeleteAllMessagesBetweenUsers_BothNull() {
        // Arrange
        String sender = null;
        String receiver = null;

        // Act
        messageService.deleteAllMessagesBetweenUsers(sender, receiver);

        // Assert
        verifyNoInteractions(messageRepository);
    }

    @Test
    void testDeleteAllMessagesBetweenUsers_RepositoryException() {
        // Arrange
        String sender = "user1";
        String receiver = "user2";
        doThrow(new RuntimeException("DB Error"))
            .when(messageRepository)
            .deleteAllByFromUserIdAndToUserId(sender, receiver);

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            messageService.deleteAllMessagesBetweenUsers(sender, receiver);
        });

        assertEquals("Error al eliminar los mensajes entre remitente y receptor", exception.getMessage());
        verify(messageRepository).deleteAllByFromUserIdAndToUserId(sender, receiver);
    }
}