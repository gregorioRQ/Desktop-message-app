package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto.ClearHistoryRequest;
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
class ClearHistoryHandlerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private SessionContext sessionContext;

    @Mock
    private WebSocketSession senderSession;

    @Mock
    private WebSocketSession recipientSession;

    @Mock
    private SessionManager.SessionInfo senderSessionInfo;

    @Mock
    private SessionManager.SessionInfo recipientSessionInfo;

    @InjectMocks
    private ClearHistoryHandler clearHistoryHandler;

    private static final String SENDER_USERNAME = "sender";
    private static final String RECIPIENT_USERNAME = "recipient";
    private static final String SENDER_SESSION_ID = "s1";

    @BeforeEach
    void setUp() {
        lenient().when(sessionContext.getSession()).thenReturn(senderSession);
        lenient().when(senderSession.getId()).thenReturn(SENDER_SESSION_ID);
        lenient().when(senderSession.isOpen()).thenReturn(true);
    }

    @Test
    void supports_ShouldReturnTrueForClearHistoryRequest() {
        WsMessage message = WsMessage.newBuilder()
                .setClearHistoryRequest(ClearHistoryRequest.getDefaultInstance())
                .build();
        assertTrue(clearHistoryHandler.supports(message));
    }

    @Test
    void handle_HappyPath_RecipientOnline() throws Exception {
        // Given
        WsMessage message = createClearHistoryRequest(RECIPIENT_USERNAME);

        when(sessionManager.getSessionInfo(SENDER_SESSION_ID)).thenReturn(senderSessionInfo);
        when(senderSessionInfo.getUsername()).thenReturn(SENDER_USERNAME);
        
        // Mockear destinatario ONLINE
        when(sessionManager.isUserOnline(RECIPIENT_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(RECIPIENT_USERNAME)).thenReturn(recipientSessionInfo);
        when(recipientSessionInfo.getWsSession()).thenReturn(recipientSession);
        when(recipientSession.isOpen()).thenReturn(true);

        // When
        clearHistoryHandler.handle(sessionContext, message);

        // Then
        // 1. Verificar eliminación en BD
        verify(messageService).deleteAllMessagesBetweenUsers(SENDER_USERNAME, RECIPIENT_USERNAME);

        // 2. Verificar reenvío al destinatario
        ArgumentCaptor<BinaryMessage> recipientCaptor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(recipientSession).sendMessage(recipientCaptor.capture());
        WsMessage forwardedMsg = WsMessage.parseFrom(recipientCaptor.getValue().getPayload().array());
        assertTrue(forwardedMsg.hasClearHistoryRequest());
        assertEquals(RECIPIENT_USERNAME, forwardedMsg.getClearHistoryRequest().getRecipient());

        // 3. Verificar respuesta de éxito al remitente
        ArgumentCaptor<BinaryMessage> senderCaptor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(senderSession).sendMessage(senderCaptor.capture());
        WsMessage responseMsg = WsMessage.parseFrom(senderCaptor.getValue().getPayload().array());
        assertTrue(responseMsg.hasClearHistoryResponse());
        assertTrue(responseMsg.getClearHistoryResponse().getSuccess());
    }

    @Test
    void handle_HappyPath_RecipientOffline() throws Exception {
        // Given
        WsMessage message = createClearHistoryRequest(RECIPIENT_USERNAME);

        when(sessionManager.getSessionInfo(SENDER_SESSION_ID)).thenReturn(senderSessionInfo);
        when(senderSessionInfo.getUsername()).thenReturn(SENDER_USERNAME);
        
        // Mockear destinatario OFFLINE
        when(sessionManager.isUserOnline(RECIPIENT_USERNAME)).thenReturn(false);

        // When
        clearHistoryHandler.handle(sessionContext, message);

        // Then
        verify(messageService).deleteAllMessagesBetweenUsers(SENDER_USERNAME, RECIPIENT_USERNAME);
        verify(recipientSession, never()).sendMessage(any()); // No se reenvía
        
        // Verificar respuesta de éxito
        verify(senderSession).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void handle_SessionInfoNotFound() throws Exception {
        // Given
        WsMessage message = createClearHistoryRequest(RECIPIENT_USERNAME);
        when(sessionManager.getSessionInfo(SENDER_SESSION_ID)).thenReturn(null);

        // When
        clearHistoryHandler.handle(sessionContext, message);

        // Then
        verify(messageService, never()).deleteAllMessagesBetweenUsers(anyString(), anyString());
        
        // Verificar respuesta de error
        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(senderSession).sendMessage(captor.capture());
        WsMessage response = WsMessage.parseFrom(captor.getValue().getPayload().array());
        assertFalse(response.getClearHistoryResponse().getSuccess());
        assertEquals("Error: sesión no encontrada", response.getClearHistoryResponse().getMessage());
    }

    @Test
    void handle_InvalidRequest_SameUser() throws Exception {
        // Given
        WsMessage message = createClearHistoryRequest(SENDER_USERNAME); // Sender = Recipient
        when(sessionManager.getSessionInfo(SENDER_SESSION_ID)).thenReturn(senderSessionInfo);
        when(senderSessionInfo.getUsername()).thenReturn(SENDER_USERNAME);

        // When
        clearHistoryHandler.handle(sessionContext, message);

        // Then
        verify(messageService, never()).deleteAllMessagesBetweenUsers(anyString(), anyString());
        
        // Verificar respuesta de error
        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(senderSession).sendMessage(captor.capture());
        WsMessage response = WsMessage.parseFrom(captor.getValue().getPayload().array());
        assertFalse(response.getClearHistoryResponse().getSuccess());
        assertEquals("Datos inválidos en la solicitud", response.getClearHistoryResponse().getMessage());
    }

    @Test
    void handle_ServiceException() throws Exception {
        // Given
        WsMessage message = createClearHistoryRequest(RECIPIENT_USERNAME);
        when(sessionManager.getSessionInfo(SENDER_SESSION_ID)).thenReturn(senderSessionInfo);
        when(senderSessionInfo.getUsername()).thenReturn(SENDER_USERNAME);
        
        // Simular error en BD
        doThrow(new RuntimeException("DB Error")).when(messageService).deleteAllMessagesBetweenUsers(SENDER_USERNAME, RECIPIENT_USERNAME);

        // When
        clearHistoryHandler.handle(sessionContext, message);

        // Then
        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(senderSession).sendMessage(captor.capture());
        WsMessage response = WsMessage.parseFrom(captor.getValue().getPayload().array());
        assertFalse(response.getClearHistoryResponse().getSuccess());
        assertEquals("Error al eliminar historial de la base de datos", response.getClearHistoryResponse().getMessage());
    }

    private WsMessage createClearHistoryRequest(String recipient) {
        return WsMessage.newBuilder()
                .setClearHistoryRequest(ClearHistoryRequest.newBuilder()
                        .setRecipient(recipient)
                        .build())
                .build();
    }
}