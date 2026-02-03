package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.PendingBlock;
import com.basic_chat.chat_service.repository.PendingBlockRepository;
import com.basic_chat.chat_service.service.BlockService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto.BlockContactRequest;
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
class BlockContactHandlerTest {

    @Mock
    private BlockService blockService;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private PendingBlockRepository pendingBlockRepository;

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
    private BlockContactHandler blockContactHandler;

    private static final String BLOCKER_USERNAME = "blocker";
    private static final String BLOCKED_USERNAME = "blocked";
    private static final String BLOCKER_SESSION_ID = "s1";

    @BeforeEach
    void setUp() {
        // Configuración común: simular que la sesión del contexto es la del bloqueador
        lenient().when(sessionContext.getSession()).thenReturn(blockerSession);
        lenient().when(blockerSession.getId()).thenReturn(BLOCKER_SESSION_ID);
    }

    @Test
    void supports_ShouldReturnTrueForBlockContactRequest() {
        WsMessage message = WsMessage.newBuilder()
                .setBlockContactRequest(BlockContactRequest.getDefaultInstance())
                .build();
        assertTrue(blockContactHandler.supports(message));
    }

    @Test
    void handle_HappyPath_UserOnline() throws Exception {
        // Given
        WsMessage message = createBlockRequest(BLOCKED_USERNAME);

        // Mockear obtención del usuario bloqueador
        when(sessionManager.getSessionInfo(BLOCKER_SESSION_ID)).thenReturn(blockerSessionInfo);
        when(blockerSessionInfo.getUsername()).thenReturn(BLOCKER_USERNAME);
        
        // Mockear éxito en el servicio de bloqueo
        when(blockService.blockUser(BLOCKER_USERNAME, BLOCKED_USERNAME)).thenReturn(true);
        
        // Mockear usuario bloqueado ONLINE y con sesión abierta
        when(sessionManager.isUserOnline(BLOCKED_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(BLOCKED_USERNAME)).thenReturn(blockedSessionInfo);
        when(blockedSessionInfo.getWsSession()).thenReturn(blockedSession);
        when(blockedSession.isOpen()).thenReturn(true);

        // When
        blockContactHandler.handle(sessionContext, message);

        // Then
        // 1. Verificar que se llamó al servicio de bloqueo
        verify(blockService).blockUser(BLOCKER_USERNAME, BLOCKED_USERNAME);

        // 2. Verificar que se envió notificación al usuario bloqueado
        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(blockedSession).sendMessage(captor.capture());
        WsMessage sentMessage = WsMessage.parseFrom(captor.getValue().getPayload().array());
        assertTrue(sentMessage.hasBlockedUsersList());
        assertEquals(BLOCKER_USERNAME, sentMessage.getBlockedUsersList().getUsers(0));

        // 3. Verificar respuesta de éxito al bloqueador
        verify(blockerSession).sendMessage(any(BinaryMessage.class));

        // 4. Verificar que NO se guardó como pendiente
        verify(pendingBlockRepository, never()).save(any(PendingBlock.class));
    }

    @Test
    void handle_HappyPath_UserOffline() throws Exception {
        // Given
        WsMessage message = createBlockRequest(BLOCKED_USERNAME);

        when(sessionManager.getSessionInfo(BLOCKER_SESSION_ID)).thenReturn(blockerSessionInfo);
        when(blockerSessionInfo.getUsername()).thenReturn(BLOCKER_USERNAME);
        when(blockService.blockUser(BLOCKER_USERNAME, BLOCKED_USERNAME)).thenReturn(true);
        
        // Mockear usuario bloqueado OFFLINE
        when(sessionManager.isUserOnline(BLOCKED_USERNAME)).thenReturn(false);

        // When
        blockContactHandler.handle(sessionContext, message);

        // Then
        // 1. Verificar que se guardó la notificación pendiente
        ArgumentCaptor<PendingBlock> pendingCaptor = ArgumentCaptor.forClass(PendingBlock.class);
        verify(pendingBlockRepository).save(pendingCaptor.capture());
        assertEquals(BLOCKER_USERNAME, pendingCaptor.getValue().getBlocker());
        assertEquals(BLOCKED_USERNAME, pendingCaptor.getValue().getBlockedUser());

        // 2. Verificar respuesta al bloqueador
        verify(blockerSession).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void handle_BlockServiceReturnsFalse() throws Exception {
        // Given
        WsMessage message = createBlockRequest(BLOCKED_USERNAME);

        when(sessionManager.getSessionInfo(BLOCKER_SESSION_ID)).thenReturn(blockerSessionInfo);
        when(blockerSessionInfo.getUsername()).thenReturn(BLOCKER_USERNAME);
        
        // Mockear FALLO en el servicio de bloqueo
        when(blockService.blockUser(BLOCKER_USERNAME, BLOCKED_USERNAME)).thenReturn(false);

        // When
        blockContactHandler.handle(sessionContext, message);

        // Then
        // Verificar respuesta de error al bloqueador
        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(blockerSession).sendMessage(captor.capture());
        WsMessage response = WsMessage.parseFrom(captor.getValue().getPayload().array());
        assertTrue(response.hasBlockContactResponse());
        assertFalse(response.getBlockContactResponse().getSuccess());
        
        // Verificar que no se intentó notificar ni guardar
        verify(sessionManager, never()).isUserOnline(anyString());
        verify(pendingBlockRepository, never()).save(any());
    }

    @Test
    void handle_UserOnlineButSessionClosed_ShouldSavePending() throws Exception {
        // Given
        WsMessage message = createBlockRequest(BLOCKED_USERNAME);

        when(sessionManager.getSessionInfo(BLOCKER_SESSION_ID)).thenReturn(blockerSessionInfo);
        when(blockerSessionInfo.getUsername()).thenReturn(BLOCKER_USERNAME);
        when(blockService.blockUser(BLOCKER_USERNAME, BLOCKED_USERNAME)).thenReturn(true);
        
        // Usuario Online pero sesión cerrada/no disponible
        when(sessionManager.isUserOnline(BLOCKED_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(BLOCKED_USERNAME)).thenReturn(blockedSessionInfo);
        when(blockedSessionInfo.getWsSession()).thenReturn(blockedSession);
        when(blockedSession.isOpen()).thenReturn(false); // Sesión cerrada

        // When
        blockContactHandler.handle(sessionContext, message);

        // Then
        // Verificar fallback a guardar pendiente
        verify(pendingBlockRepository).save(any(PendingBlock.class));
    }

    @Test
    void handle_UserOnline_NotificationException_ShouldFallbackToPending() throws Exception {
        // Test para cubrir el bloque catch en notifyBlockedUserIfOnline
        // Given
        WsMessage message = createBlockRequest(BLOCKED_USERNAME);

        when(sessionManager.getSessionInfo(BLOCKER_SESSION_ID)).thenReturn(blockerSessionInfo);
        when(blockerSessionInfo.getUsername()).thenReturn(BLOCKER_USERNAME);
        when(blockService.blockUser(BLOCKER_USERNAME, BLOCKED_USERNAME)).thenReturn(true);
        
        // Usuario Online
        when(sessionManager.isUserOnline(BLOCKED_USERNAME)).thenReturn(true);
        // Simular excepción al intentar notificar (ej. error inesperado al buscar sesión)
        when(sessionManager.findByUsername(BLOCKED_USERNAME)).thenThrow(new RuntimeException("Error inesperado"));

        // When
        blockContactHandler.handle(sessionContext, message);

        // Then
        // Verificar que el catch del método privado captura la excepción y llama a savePendingBlockNotification
        verify(pendingBlockRepository).save(any(PendingBlock.class));
        
        // Verificar que el flujo continúa y responde al bloqueador
        verify(blockerSession).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void handle_SavePendingException_ShouldNotCrash() throws Exception {
        // Test para cubrir el bloque catch en savePendingBlockNotification
        // Given
        WsMessage message = createBlockRequest(BLOCKED_USERNAME);

        when(sessionManager.getSessionInfo(BLOCKER_SESSION_ID)).thenReturn(blockerSessionInfo);
        when(blockerSessionInfo.getUsername()).thenReturn(BLOCKER_USERNAME);
        when(blockService.blockUser(BLOCKER_USERNAME, BLOCKED_USERNAME)).thenReturn(true);
        
        // Usuario Offline para forzar guardado pendiente
        when(sessionManager.isUserOnline(BLOCKED_USERNAME)).thenReturn(false);
        
        // Simular error de base de datos al guardar
        doThrow(new RuntimeException("DB Error")).when(pendingBlockRepository).save(any(PendingBlock.class));

        // When & Then
        // No debería lanzar excepción porque el método privado captura el error (fail-safe)
        assertDoesNotThrow(() -> blockContactHandler.handle(sessionContext, message));
        
        // Verificar que aún así se responde al cliente
        verify(blockerSession).sendMessage(any(BinaryMessage.class));
    }

    private WsMessage createBlockRequest(String recipient) {
        return WsMessage.newBuilder()
                .setBlockContactRequest(BlockContactRequest.newBuilder()
                        .setRecipient(recipient)
                        .build())
                .build();
    }
}