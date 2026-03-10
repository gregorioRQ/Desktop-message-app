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
class MessageServiceGetAndDeleteUnreadMessagesTest {

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
    void testGetAndDeleteUnreadMessages_HappyPath() {
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
        List<ChatMessage> result = messageService.getAndDeleteUnreadMessages(username);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("100", result.get(0).getId());
        assertEquals("Hello World", result.get(0).getContent());

        // Verifica que se eliminaron los mensajes de la base de datos
        verify(messageRepository).findByToUserIdAndSeenFalse(username);
        verify(messageRepository).deleteAllByIdIn(List.of(100L));
    }

    @Test
    void testGetAndDeleteUnreadMessages_NullUsername() {
        // Act
        List<ChatMessage> result = messageService.getAndDeleteUnreadMessages(null);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(messageRepository);
    }

    @Test
    void testGetAndDeleteUnreadMessages_NoMessagesFound() {
        // Arrange
        String username = "user1";
        when(messageRepository.findByToUserIdAndSeenFalse(username))
                .thenReturn(Collections.emptyList());

        // Act
        List<ChatMessage> result = messageService.getAndDeleteUnreadMessages(username);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verify(messageRepository).findByToUserIdAndSeenFalse(username);
        verify(messageRepository, never()).deleteAllByIdIn(anyList());
    }

    @Test
    void testGetAndDeleteUnreadMessages_MultipleMessages() {
        // Arrange
        String username = "user1";
        
        ChatMessage protoMsg1 = ChatMessage.newBuilder()
                .setId("100")
                .setSender("sender1")
                .setRecipient(username)
                .setContent("Message 1")
                .build();

        ChatMessage protoMsg2 = ChatMessage.newBuilder()
                .setId("200")
                .setSender("sender2")
                .setRecipient(username)
                .setContent("Message 2")
                .build();

        ChatMessage protoMsg3 = ChatMessage.newBuilder()
                .setId("300")
                .setSender("sender3")
                .setRecipient(username)
                .setContent("Message 3")
                .build();

        Message messageEntity1 = new Message();
        messageEntity1.setId(100L);
        messageEntity1.setToUserId(username);
        messageEntity1.setSeen(false);
        messageEntity1.setData(protoMsg1.toByteArray());

        Message messageEntity2 = new Message();
        messageEntity2.setId(200L);
        messageEntity2.setToUserId(username);
        messageEntity2.setSeen(false);
        messageEntity2.setData(protoMsg2.toByteArray());

        Message messageEntity3 = new Message();
        messageEntity3.setId(300L);
        messageEntity3.setToUserId(username);
        messageEntity3.setSeen(false);
        messageEntity3.setData(protoMsg3.toByteArray());

        when(messageRepository.findByToUserIdAndSeenFalse(username))
                .thenReturn(List.of(messageEntity1, messageEntity2, messageEntity3));

        // Act
        List<ChatMessage> result = messageService.getAndDeleteUnreadMessages(username);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
        assertEquals("100", result.get(0).getId());
        assertEquals("200", result.get(1).getId());
        assertEquals("300", result.get(2).getId());

        // Verifica que se eliminaron todos los mensajes
        verify(messageRepository).deleteAllByIdIn(List.of(100L, 200L, 300L));
    }

    @Test
    void testGetAndDeleteUnreadMessages_RepositoryThrowsException() {
        // Arrange
        String username = "user1";
        when(messageRepository.findByToUserIdAndSeenFalse(username))
                .thenThrow(new RuntimeException("Database error"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            messageService.getAndDeleteUnreadMessages(username);
        });

        assertEquals("No se pudieron procesar los mensajes pendientes", exception.getMessage());
        verify(messageRepository).findByToUserIdAndSeenFalse(username);
    }

    @Test
    void testGetAndDeleteUnreadMessages_ProtobufParsingError() {
        // Arrange
        String username = "user1";
        Message messageEntity = new Message();
        messageEntity.setId(100L);
        messageEntity.setData(new byte[]{1, 2, 3, 4}); // Invalid protobuf data

        when(messageRepository.findByToUserIdAndSeenFalse(username))
                .thenReturn(List.of(messageEntity));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            messageService.getAndDeleteUnreadMessages(username);
        });

        assertEquals("No se pudieron procesar los mensajes pendientes", exception.getMessage());
    }

    @Test
    void testGetAndDeleteUnreadMessages_VerifyMessagesAreDeleted() {
        // Arrange
        String username = "user1";
        
        ChatMessage protoMsg1 = ChatMessage.newBuilder()
                .setId("100")
                .setSender("sender1")
                .setRecipient(username)
                .setContent("Message 1")
                .build();

        ChatMessage protoMsg2 = ChatMessage.newBuilder()
                .setId("200")
                .setSender("sender2")
                .setRecipient(username)
                .setContent("Message 2")
                .build();

        Message messageEntity1 = new Message();
        messageEntity1.setId(100L);
        messageEntity1.setToUserId(username);
        messageEntity1.setSeen(false);
        messageEntity1.setData(protoMsg1.toByteArray());

        Message messageEntity2 = new Message();
        messageEntity2.setId(200L);
        messageEntity2.setToUserId(username);
        messageEntity2.setSeen(false);
        messageEntity2.setData(protoMsg2.toByteArray());

        when(messageRepository.findByToUserIdAndSeenFalse(username))
                .thenReturn(List.of(messageEntity1, messageEntity2));

        // Act
        List<ChatMessage> result = messageService.getAndDeleteUnreadMessages(username);

        // Assert - Verifica que los mensajes fueron retornados Y eliminados
        assertEquals(2, result.size());
        
        // Verifica la eliminación en el orden correcto
        verify(messageRepository, times(1)).deleteAllByIdIn(anyList());
    }
}
