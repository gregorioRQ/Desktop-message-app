package com.basic_chat.chat_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.chat_service.repository.PendingBlockRepository;
import com.basic_chat.chat_service.repository.PendingClearHistoryRepository;
import com.basic_chat.chat_service.repository.PendingContactIdentityRepository;
import com.basic_chat.chat_service.repository.PendingDeletionRepository;
import com.basic_chat.chat_service.repository.PendingReadReceiptRepository;
import com.basic_chat.chat_service.repository.PendingUnblockRepository;
import com.basic_chat.chat_service.validator.MessageValidator;
import com.basic_chat.proto.MessagesProto;

@ExtendWith(MockitoExtension.class)
@DisplayName("Tests for MessageService.getAllPendingMessages()")
class MessageServiceGetAllPendingMessagesTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private PendingDeletionRepository pendingDeletionRepository;

    @Mock
    private PendingReadReceiptRepository pendingReadReceiptRepository;

    @Mock
    private PendingBlockRepository pendingBlockRepository;

    @Mock
    private PendingUnblockRepository pendingUnblockRepository;

    @Mock
    private PendingClearHistoryRepository pendingClearHistoryRepository;

    @Mock
    private PendingContactIdentityRepository pendingContactIdentityRepository;

    @Mock
    private MessageValidator messageValidator;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(
                messageRepository,
                pendingDeletionRepository,
                pendingReadReceiptRepository,
                pendingBlockRepository,
                pendingUnblockRepository,
                pendingClearHistoryRepository,
                pendingContactIdentityRepository,
                messageValidator
        );
    }

    @Test
    @DisplayName("Debe eliminar mensajes de la BD después de retornarlos al cliente")
    void getAllPendingMessages_DebeEliminarMensajesDespuesDeEntregarlos() {
        // Arrange
        String username = "testUser";

        Message messageEntity = new Message();
        messageEntity.setId(100L);
        messageEntity.setToUserId(username);
        messageEntity.setFromUserId("sender1");
        messageEntity.setSeen(false);

        com.basic_chat.proto.MessagesProto.ChatMessage protoMsg = com.basic_chat.proto.MessagesProto.ChatMessage.newBuilder()
                .setId("100")
                .setSender("sender1")
                .setRecipient(username)
                .setContent("Hello")
                .build();

        messageEntity.setData(protoMsg.toByteArray());

        when(messageRepository.findByToUserIdAndSeenFalse(username))
                .thenReturn(List.of(messageEntity));

        lenient().when(pendingDeletionRepository.findByRecipient(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingBlockRepository.findByBlockedUser(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingUnblockRepository.findByUnblockedUser(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingClearHistoryRepository.findByRecipient(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingReadReceiptRepository.findByReceiptRecipient(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingContactIdentityRepository.findByRecipient(username)).thenReturn(Collections.emptyList());

        // Act
        MessagesProto.WsMessage result = messageService.getAllPendingMessages(username);

        // Assert
        assertNotNull(result);
        assertTrue(result.hasUnreadMessagesList());
        assertEquals(1, result.getUnreadMessagesList().getMessagesList().size());

        // Verificar que se llamó al método de eliminación con los IDs correctos
        verify(messageRepository).deleteAllByIdIn(List.of(100L));
    }

    @Test
    @DisplayName("Debe continuar si la eliminación de mensajes falla")
    void getAllPendingMessages_DebeContinuarSiEliminacionFalla() {
        // Arrange
        String username = "testUser";

        Message messageEntity = new Message();
        messageEntity.setId(100L);
        messageEntity.setToUserId(username);
        messageEntity.setFromUserId("sender1");
        messageEntity.setSeen(false);

        com.basic_chat.proto.MessagesProto.ChatMessage protoMsg = com.basic_chat.proto.MessagesProto.ChatMessage.newBuilder()
                .setId("100")
                .setSender("sender1")
                .setRecipient(username)
                .setContent("Hello")
                .build();

        messageEntity.setData(protoMsg.toByteArray());

        when(messageRepository.findByToUserIdAndSeenFalse(username))
                .thenReturn(List.of(messageEntity));

        // Simular que la eliminación falla
        doThrow(new RuntimeException("Database error"))
                .when(messageRepository).deleteAllByIdIn(anyList());

        lenient().when(pendingDeletionRepository.findByRecipient(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingBlockRepository.findByBlockedUser(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingUnblockRepository.findByUnblockedUser(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingClearHistoryRepository.findByRecipient(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingReadReceiptRepository.findByReceiptRecipient(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingContactIdentityRepository.findByRecipient(username)).thenReturn(Collections.emptyList());

        // Act - No debe lanzar excepción
        MessagesProto.WsMessage result = messageService.getAllPendingMessages(username);

        // Assert - El resultado debe ser no null y contener los mensajes
        assertNotNull(result);
        assertTrue(result.hasUnreadMessagesList());
    }

    @Test
    @DisplayName("Debe retornar null cuando no hay mensajes pendientes")
    void getAllPendingMessages_DebeRetornarNullSinPendientes() {
        // Arrange
        String username = "testUser";

        lenient().when(messageRepository.findByToUserIdAndSeenFalse(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingDeletionRepository.findByRecipient(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingBlockRepository.findByBlockedUser(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingUnblockRepository.findByUnblockedUser(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingClearHistoryRepository.findByRecipient(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingReadReceiptRepository.findByReceiptRecipient(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingContactIdentityRepository.findByRecipient(username)).thenReturn(Collections.emptyList());

        // Act
        MessagesProto.WsMessage result = messageService.getAllPendingMessages(username);

        // Assert
        assertNull(result);
    }

    @Test
    @DisplayName("Debe eliminar múltiples mensajes correctamente")
    void getAllPendingMessages_DebeEliminarMultiplesMensajes() {
        // Arrange
        String username = "testUser";

        Message msg1 = new Message();
        msg1.setId(100L);
        msg1.setToUserId(username);
        msg1.setFromUserId("sender1");
        msg1.setSeen(false);

        Message msg2 = new Message();
        msg2.setId(200L);
        msg2.setToUserId(username);
        msg2.setFromUserId("sender2");
        msg2.setSeen(false);

        com.basic_chat.proto.MessagesProto.ChatMessage protoMsg1 = com.basic_chat.proto.MessagesProto.ChatMessage.newBuilder()
                .setId("100")
                .setSender("sender1")
                .setRecipient(username)
                .setContent("Hello 1")
                .build();

        com.basic_chat.proto.MessagesProto.ChatMessage protoMsg2 = com.basic_chat.proto.MessagesProto.ChatMessage.newBuilder()
                .setId("200")
                .setSender("sender2")
                .setRecipient(username)
                .setContent("Hello 2")
                .build();

        msg1.setData(protoMsg1.toByteArray());
        msg2.setData(protoMsg2.toByteArray());

        when(messageRepository.findByToUserIdAndSeenFalse(username))
                .thenReturn(List.of(msg1, msg2));

        lenient().when(pendingDeletionRepository.findByRecipient(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingBlockRepository.findByBlockedUser(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingUnblockRepository.findByUnblockedUser(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingClearHistoryRepository.findByRecipient(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingReadReceiptRepository.findByReceiptRecipient(username)).thenReturn(Collections.emptyList());
        lenient().when(pendingContactIdentityRepository.findByRecipient(username)).thenReturn(Collections.emptyList());

        // Act
        MessagesProto.WsMessage result = messageService.getAllPendingMessages(username);

        // Assert
        assertNotNull(result);
        assertTrue(result.hasUnreadMessagesList());
        assertEquals(2, result.getUnreadMessagesList().getMessagesList().size());

        // Verificar que se eliminaron ambos mensajes
        verify(messageRepository).deleteAllByIdIn(List.of(100L, 200L));
    }
}
