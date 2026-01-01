package com.basic_chat.chat_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.chat_service.validator.MessageValidator;
import com.basic_chat.proto.MessagesProto;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService - saveMessage")
class MessageServiceSaveMessageTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private MessageValidator messageValidator;

    @InjectMocks
    private MessageService messageService;

    private MessagesProto.ChatMessage validMessage;

    @BeforeEach
    void setUp() {
        validMessage = MessagesProto.ChatMessage.newBuilder()
                .setId("1")
                .setSender("user1")
                .setRecipient("user2")
                .setContent("Hello")
                .setTimestamp(System.currentTimeMillis())
                .build();
    }

    @Test
    @DisplayName("Debe guardar un mensaje válido exitosamente")
    void shouldSaveValidMessageSuccessfully() {
        // Arrange
        when(messageRepository.save(any(Message.class))).thenReturn(new Message());

        // Act
        assertDoesNotThrow(() -> messageService.saveMessage(validMessage));

        // Assert
        verify(messageRepository, times(1)).save(any(Message.class));
        verify(messageValidator, times(1)).validate(validMessage);
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando el mensaje es nulo")
    void shouldThrowExceptionWhenMessageIsNull() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> messageService.saveMessage(null),
                "Debería lanzar IllegalArgumentException cuando mensaje es nulo"
        );

        assertEquals("El mensaje no puede ser nulo", exception.getMessage());
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando el ID está vacío")
    void shouldThrowExceptionWhenIdIsEmpty() {
        // Arrange
        MessagesProto.ChatMessage messageWithoutId = MessagesProto.ChatMessage.newBuilder()
                .setId("")
                .setSender("user1")
                .setRecipient("user2")
                .setContent("Hello")
                .setTimestamp(System.currentTimeMillis())
                .build();

        doThrow(new IllegalArgumentException("El ID del mensaje no puede estar vacío"))
                .when(messageValidator).validate(messageWithoutId);

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> messageService.saveMessage(messageWithoutId)
        );
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando el sender está vacío")
    void shouldThrowExceptionWhenSenderIsEmpty() {
        // Arrange
        MessagesProto.ChatMessage messageWithoutSender = MessagesProto.ChatMessage.newBuilder()
                .setId("1")
                .setSender("")
                .setRecipient("user2")
                .setContent("Hello")
                .setTimestamp(System.currentTimeMillis())
                .build();

        doThrow(new IllegalArgumentException("El remitente (sender) no puede estar vacío"))
                .when(messageValidator).validate(messageWithoutSender);

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> messageService.saveMessage(messageWithoutSender)
        );
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando el recipient está vacío")
    void shouldThrowExceptionWhenRecipientIsEmpty() {
        // Arrange
        MessagesProto.ChatMessage messageWithoutRecipient = MessagesProto.ChatMessage.newBuilder()
                .setId("1")
                .setSender("user1")
                .setRecipient("")
                .setContent("Hello")
                .setTimestamp(System.currentTimeMillis())
                .build();

        doThrow(new IllegalArgumentException("El destinatario (recipient) no puede estar vacío"))
                .when(messageValidator).validate(messageWithoutRecipient);

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> messageService.saveMessage(messageWithoutRecipient)
        );
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    @DisplayName("Debe lanzar excepción cuando el content está vacío")
    void shouldThrowExceptionWhenContentIsEmpty() {
        // Arrange
        MessagesProto.ChatMessage messageWithoutContent = MessagesProto.ChatMessage.newBuilder()
                .setId("1")
                .setSender("user1")
                .setRecipient("user2")
                .setContent("")
                .setTimestamp(System.currentTimeMillis())
                .build();

        doThrow(new IllegalArgumentException("El contenido del mensaje no puede estar vacío"))
                .when(messageValidator).validate(messageWithoutContent);

        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> messageService.saveMessage(messageWithoutContent)
        );
        verify(messageRepository, never()).save(any(Message.class));
    }

    @Test
    @DisplayName("Debe manejar excepción cuando el repositorio falla")
    void shouldThrowRuntimeExceptionWhenRepositoryFails() {
        // Arrange
        when(messageRepository.save(any(Message.class)))
                .thenThrow(new RuntimeException("Error en BD"));

        // Act & Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> messageService.saveMessage(validMessage)
        );

        assertTrue(exception.getMessage().contains("Error al guardar el mensaje"));
        verify(messageRepository, times(1)).save(any(Message.class));
    }

    @Test
    @DisplayName("Debe mapear correctamente los campos del mensaje")
    void shouldMapMessageFieldsCorrectly() {
        // Arrange
        when(messageRepository.save(any(Message.class))).thenReturn(new Message());

        // Act
        messageService.saveMessage(validMessage);

        // Assert
        verify(messageRepository).save(argThat(message ->
                message.getFromUserId().equals("user1") &&
                message.getToUserId().equals("user2") &&
                new String(message.getData()).equals("Hello") &&
                message.getCreationTime() == validMessage.getTimestamp()
        ));
    }
}
