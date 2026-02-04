package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.models.PendingContactIdentity;
import com.basic_chat.chat_service.models.PendingReadReceipt;
import com.basic_chat.chat_service.models.PendingUnblock;
import com.basic_chat.chat_service.repository.PendingContactIdentityRepository;
import com.basic_chat.chat_service.repository.PendingUnblockRepository;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.proto.MessagesProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PendingMessagesHandlerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private PendingUnblockRepository pendingUnblockRepository;

    @Mock
    private PendingContactIdentityRepository pendingContactIdentityRepository;

    @Mock
    private WebSocketSession session;

    @InjectMocks
    private PendingMessagesHandler pendingMessagesHandler;

    private static final String USERNAME = "testUser";

    @BeforeEach
    void setUp() {
        // No es necesario mockear session.isOpen() porque los métodos no lo comprueban directamente,
        // confían en que la sesión es válida al momento de la llamada.
    }

    // --- Tests for sendPendingMessages ---

    @Test
    void sendPendingMessages_ShouldSendMessages_WhenTheyExist() throws IOException {
        // Given
        List<MessagesProto.ChatMessage> messages = List.of(MessagesProto.ChatMessage.newBuilder().setId("1").build());
        when(messageService.getUnreadMessages(USERNAME)).thenReturn(messages);

        // When
        pendingMessagesHandler.sendPendingMessages(session, USERNAME);

        // Then
        verify(session).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void sendPendingMessages_ShouldDoNothing_WhenNoMessagesExist() throws IOException {
        // Given
        when(messageService.getUnreadMessages(USERNAME)).thenReturn(Collections.emptyList());

        // When
        pendingMessagesHandler.sendPendingMessages(session, USERNAME);

        // Then
        verify(session, never()).sendMessage(any());
    }

    @Test
    void sendPendingMessages_ShouldThrowIOException_WhenSendFails() throws IOException {
        // Given
        List<MessagesProto.ChatMessage> messages = List.of(MessagesProto.ChatMessage.newBuilder().setId("1").build());
        when(messageService.getUnreadMessages(USERNAME)).thenReturn(messages);
        doThrow(new IOException("Network error")).when(session).sendMessage(any(BinaryMessage.class));

        // When & Then
        assertThrows(IOException.class, () -> pendingMessagesHandler.sendPendingMessages(session, USERNAME));
    }

    // --- Tests for sendPendingDeletions ---

    @Test
    void sendPendingDeletions_ShouldSendDeletions_WhenTheyExist() throws IOException {
        // Given
        List<String> deletionIds = List.of("msg1", "msg2");
        when(messageService.getAndClearPendingDeletions(USERNAME)).thenReturn(deletionIds);

        // When
        pendingMessagesHandler.sendPendingDeletions(session, USERNAME);

        // Then
        verify(session, times(2)).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void sendPendingDeletions_ShouldDoNothing_WhenNoDeletionsExist() throws IOException {
        // Given
        when(messageService.getAndClearPendingDeletions(USERNAME)).thenReturn(Collections.emptyList());

        // When
        pendingMessagesHandler.sendPendingDeletions(session, USERNAME);

        // Then
        verify(session, never()).sendMessage(any());
    }

    // --- Tests for sendPendingUnblocks ---

    @Test
    void sendPendingUnblocks_ShouldSendAndClearUnblocks_WhenTheyExist() throws IOException {
        // Given
        List<PendingUnblock> unblocks = List.of(new PendingUnblock(1L, "blocker", USERNAME, 0L));
        when(pendingUnblockRepository.findByUnblockedUser(USERNAME)).thenReturn(unblocks);

        // When
        pendingMessagesHandler.sendPendingUnblocks(session, USERNAME);

        // Then
        verify(session).sendMessage(any(BinaryMessage.class));
        verify(pendingUnblockRepository).deleteAll(unblocks);
    }

    @Test
    void sendPendingUnblocks_ShouldDoNothing_WhenNoUnblocksExist() throws IOException {
        // Given
        when(pendingUnblockRepository.findByUnblockedUser(USERNAME)).thenReturn(Collections.emptyList());

        // When
        pendingMessagesHandler.sendPendingUnblocks(session, USERNAME);

        // Then
        verify(session, never()).sendMessage(any());
        verify(pendingUnblockRepository, never()).deleteAll(any());
    }

    // --- Tests for sendPendingReadReceipts ---

    @Test
    void sendPendingReadReceipts_ShouldSendReceipts_WhenTheyExist() throws IOException {
        // Given
        List<PendingReadReceipt> receipts = List.of(new PendingReadReceipt(1L, "msg1", USERNAME, "reader"));
        when(messageService.getAndClearPendingReadReceipts(USERNAME)).thenReturn(receipts);

        // When
        pendingMessagesHandler.sendPendingReadReceipts(session, USERNAME);

        // Then
        verify(session).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void sendPendingReadReceipts_ShouldHandleSendErrorGracefully() throws IOException {
        // Given
        List<PendingReadReceipt> receipts = List.of(new PendingReadReceipt(1L, "msg1", USERNAME, "reader"));
        when(messageService.getAndClearPendingReadReceipts(USERNAME)).thenReturn(receipts);
        doThrow(new IOException("Network error")).when(session).sendMessage(any(BinaryMessage.class));

        // When & Then
        // El método interno lanza RuntimeException, que es atrapada y re-lanzada como IOException
        assertThrows(IOException.class, () -> pendingMessagesHandler.sendPendingReadReceipts(session, USERNAME));
    }

    // --- Tests for sendPendingContactIdentities ---

    @Test
    void sendPendingContactIdentities_ShouldSendAndClearIdentities_WhenTheyExist() throws IOException {
        // Given
        List<PendingContactIdentity> identities = List.of(new PendingContactIdentity(1L, USERNAME, "senderId", "sender"));
        when(pendingContactIdentityRepository.findByRecipient(USERNAME)).thenReturn(identities);

        // When
        pendingMessagesHandler.sendPendingContactIdentities(session, USERNAME);

        // Then
        verify(session).sendMessage(any(BinaryMessage.class));
        verify(pendingContactIdentityRepository).deleteAll(identities);
    }

    @Test
    void sendPendingContactIdentities_ShouldDoNothing_WhenNoIdentitiesExist() throws IOException {
        // Given
        when(pendingContactIdentityRepository.findByRecipient(USERNAME)).thenReturn(Collections.emptyList());

        // When
        pendingMessagesHandler.sendPendingContactIdentities(session, USERNAME);

        // Then
        verify(session, never()).sendMessage(any());
        verify(pendingContactIdentityRepository, never()).deleteAll(any());
    }

    @Test
    void sendPendingContactIdentities_ShouldNotDelete_WhenSendFails() throws IOException {
        // Given
        List<PendingContactIdentity> identities = List.of(new PendingContactIdentity(1L, USERNAME, "senderId", "sender"));
        when(pendingContactIdentityRepository.findByRecipient(USERNAME)).thenReturn(identities);
        doThrow(new IOException("Network error")).when(session).sendMessage(any(BinaryMessage.class));

        // When & Then
        assertThrows(IOException.class, () -> pendingMessagesHandler.sendPendingContactIdentities(session, USERNAME));
        verify(pendingContactIdentityRepository, never()).deleteAll(any());
    }
}