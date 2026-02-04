package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.PendingUnblock;
import com.basic_chat.chat_service.repository.PendingUnblockRepository;
import com.basic_chat.chat_service.service.BlockService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto.UnblockContactRequest;
import com.basic_chat.proto.MessagesProto.WsMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnblockContactHandlerTest {

    @Mock
    private BlockService blockService;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private PendingUnblockRepository pendingUnblockRepository;
    @Mock
    private SessionContext sessionContext;
    @Mock
    private WebSocketSession blockerSession;
    @Mock
    private WebSocketSession blockedSession;
    @Mock
    private SessionManager.SessionInfo blockerSessionInfo;
    @Mock
    private SessionManager.SessionInfo blockedSessionInfo;

    @InjectMocks
    private UnblockContactHandler unblockContactHandler;

    private static final String BLOCKER_USERNAME = "blocker";
    private static final String BLOCKED_USERNAME = "blocked";
    private static final String BLOCKER_SESSION_ID = "s1";

    @BeforeEach
    void setUp() {
        lenient().when(sessionContext.getSession()).thenReturn(blockerSession);
        lenient().when(blockerSession.getId()).thenReturn(BLOCKER_SESSION_ID);
    }

    @Test
    void supports_ShouldReturnTrue() {
        WsMessage message = WsMessage.newBuilder()
                .setUnblockContactRequest(UnblockContactRequest.getDefaultInstance())
                .build();
        assertTrue(unblockContactHandler.supports(message));
    }

    @Test
    void handle_HappyPath_UserOnline() throws Exception {
        // Given
        WsMessage message = createUnblockRequest(BLOCKED_USERNAME);

        when(sessionManager.getSessionInfo(BLOCKER_SESSION_ID)).thenReturn(blockerSessionInfo);
        when(blockerSessionInfo.getUsername()).thenReturn(BLOCKER_USERNAME);
        when(blockService.unblockUser(BLOCKER_USERNAME, BLOCKED_USERNAME)).thenReturn(true);
        
        when(sessionManager.isUserOnline(BLOCKED_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(BLOCKED_USERNAME)).thenReturn(blockedSessionInfo);
        when(blockedSessionInfo.getWsSession()).thenReturn(blockedSession);

        // When
        unblockContactHandler.handle(sessionContext, message);

        // Then
        verify(blockService).unblockUser(BLOCKER_USERNAME, BLOCKED_USERNAME);
        
        // Verificar notificación al usuario desbloqueado
        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(blockedSession).sendMessage(captor.capture());
        WsMessage notif = WsMessage.parseFrom(captor.getValue().getPayload().array());
        assertTrue(notif.hasUnblockedUsersList());
        assertEquals(BLOCKER_USERNAME, notif.getUnblockedUsersList().getUsers(0));

        // Verificar respuesta al bloqueador
        verify(blockerSession).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void handle_HappyPath_UserOffline() throws Exception {
        // Given
        WsMessage message = createUnblockRequest(BLOCKED_USERNAME);

        when(sessionManager.getSessionInfo(BLOCKER_SESSION_ID)).thenReturn(blockerSessionInfo);
        when(blockerSessionInfo.getUsername()).thenReturn(BLOCKER_USERNAME);
        when(blockService.unblockUser(BLOCKER_USERNAME, BLOCKED_USERNAME)).thenReturn(true);
        when(sessionManager.isUserOnline(BLOCKED_USERNAME)).thenReturn(false);

        // When
        unblockContactHandler.handle(sessionContext, message);

        // Then
        ArgumentCaptor<PendingUnblock> captor = ArgumentCaptor.forClass(PendingUnblock.class);
        verify(pendingUnblockRepository).save(captor.capture());
        PendingUnblock savedPending = captor.getValue();
        assertEquals(BLOCKER_USERNAME, savedPending.getBlocker());
        assertEquals(BLOCKED_USERNAME, savedPending.getUnblockedUser());

        verify(blockerSession).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void handle_ServiceReturnsFalse() throws Exception {
        // Given
        WsMessage message = createUnblockRequest(BLOCKED_USERNAME);

        when(sessionManager.getSessionInfo(BLOCKER_SESSION_ID)).thenReturn(blockerSessionInfo);
        when(blockerSessionInfo.getUsername()).thenReturn(BLOCKER_USERNAME);
        when(blockService.unblockUser(BLOCKER_USERNAME, BLOCKED_USERNAME)).thenReturn(false);

        // When
        unblockContactHandler.handle(sessionContext, message);

        // Then
        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(blockerSession).sendMessage(captor.capture());
        WsMessage response = WsMessage.parseFrom(captor.getValue().getPayload().array());
        assertFalse(response.getUnblockContactResponse().getSuccess());
        
        verify(sessionManager, never()).isUserOnline(anyString());
    }

    @Test
    void handle_UserOnlineButSessionMissing_ShouldSavePending() throws Exception {
        // Given
        WsMessage message = createUnblockRequest(BLOCKED_USERNAME);

        when(sessionManager.getSessionInfo(BLOCKER_SESSION_ID)).thenReturn(blockerSessionInfo);
        when(blockerSessionInfo.getUsername()).thenReturn(BLOCKER_USERNAME);
        when(blockService.unblockUser(BLOCKER_USERNAME, BLOCKED_USERNAME)).thenReturn(true);
        
        when(sessionManager.isUserOnline(BLOCKED_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(BLOCKED_USERNAME)).thenReturn(null);

        // When
        unblockContactHandler.handle(sessionContext, message);

        // Then
        verify(pendingUnblockRepository).save(any(PendingUnblock.class));
    }

    private WsMessage createUnblockRequest(String recipient) {
        return WsMessage.newBuilder()
                .setUnblockContactRequest(UnblockContactRequest.newBuilder().setRecipient(recipient).build())
                .build();
    }
}