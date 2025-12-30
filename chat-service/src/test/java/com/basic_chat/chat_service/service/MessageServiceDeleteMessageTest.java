package com.basic_chat.chat_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.chat_service.validator.MessageValidator;
import com.basic_chat.proto.MessagesProto;

import jakarta.persistence.EntityNotFoundException;

@DisplayName("MessageService - deleteMessage Tests")
class MessageServiceDeleteMessageTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageValidator messageValidator;

    @InjectMocks
    private MessageService messageService;

    private Message testMessage;
    private MessagesProto.DeleteMessageRequest deleteRequest;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        testMessage = new Message();
        testMessage.setId(1L);
        testMessage.setFromUserId("user1");
        testMessage.setToUserId("user2");
        
        deleteRequest = MessagesProto.DeleteMessageRequest.newBuilder()
                .setMessageId("1")
                .build();
    }

    @Test
    @DisplayName("Debe eliminar mensaje exitosamente cuando existe")
    void testDeleteMessageSuccessfully() {
        // Arrange
        when(messageValidator.validateAndGetMessage(1L)).thenReturn(testMessage);
        
        // Act
        messageService.deleteMessage(deleteRequest);
        
        // Assert
        verify(messageValidator, times(1)).validateAndGetMessage(1L);
        verify(messageValidator, times(1)).validateMessageId(testMessage);
        verify(messageRepository, times(1)).deleteById(1L);
    }

    @Test
    @DisplayName("Debe lanzar EntityNotFoundException cuando el mensaje no existe")
    void testDeleteMessageNotFound() {
        // Arrange
        when(messageValidator.validateAndGetMessage(1L))
                .thenThrow(new EntityNotFoundException("El mensaje con ID 1 no existe"));
        
        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> {
            messageService.deleteMessage(deleteRequest);
        });
        
        verify(messageRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("Debe lanzar IllegalArgumentException cuando el ID es nulo")
    void testDeleteMessageWithNullId() {
        // Arrange
        when(messageValidator.validateAndGetMessage(null))
                .thenThrow(new IllegalArgumentException("El ID del mensaje no puede ser nulo"));
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            MessagesProto.DeleteMessageRequest invalidRequest = MessagesProto.DeleteMessageRequest.newBuilder()
                    .setMessageId("")
                    .build();
            messageService.deleteMessage(invalidRequest);
        });
        
        verify(messageRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("Debe lanzar IllegalArgumentException cuando el mensaje tiene ID nulo")
    void testDeleteMessageWithNullMessageId() {
        // Arrange
        Message invalidMessage = new Message();
        invalidMessage.setId(null);
        
        when(messageValidator.validateAndGetMessage(1L)).thenReturn(invalidMessage);
        doThrow(new IllegalArgumentException("El ID del mensaje es nulo"))
                .when(messageValidator).validateMessageId(invalidMessage);
        
        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            messageService.deleteMessage(deleteRequest);
        });
        
        verify(messageRepository, never()).deleteById(anyLong());
    }
}
