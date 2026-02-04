package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.chat_service.util.WebSocketValidationUtil;
import com.basic_chat.proto.MessagesProto.DeleteMessageRequest;
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
class DeleteMessageHandlerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private WebSocketValidationUtil validationUtil;

    @Mock
    private SessionContext sessionContext;

    @Mock
    private WebSocketSession senderSession;

    @Mock
    private WebSocketSession recipientSession;

    @Mock
    private SessionManager.SessionInfo recipientSessionInfo;

    @InjectMocks
    private DeleteMessageHandler deleteMessageHandler;

    private static final String MESSAGE_ID = "msg-123";
    private static final String RECIPIENT_USERNAME = "recipient";

    @BeforeEach
    void setUp() {
        // Configuración por defecto para validaciones exitosas
        lenient().when(validationUtil.isValidContext(any())).thenReturn(true);
        lenient().when(validationUtil.isValidProtobufField(anyBoolean(), anyString())).thenReturn(true);
        lenient().when(validationUtil.isValidString(anyString(), anyString())).thenReturn(true);
        lenient().when(validationUtil.isValidWebSocketSession(any())).thenReturn(true);
        lenient().when(validationUtil.isValidStrings(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        lenient().when(sessionContext.getSession()).thenReturn(senderSession);
    }

    @Test
    void supports_ShouldReturnTrueForDeleteMessageRequest() {
        WsMessage message = WsMessage.newBuilder()
                .setDeleteMessageRequest(DeleteMessageRequest.getDefaultInstance())
                .build();
        assertTrue(deleteMessageHandler.supports(message));
    }

    @Test
    void handle_HappyPath_RecipientOnline() throws Exception {
        // Given
        WsMessage message = createDeleteRequest(MESSAGE_ID);
        Message deletedMessage = new Message();
        deletedMessage.setToUserId(RECIPIENT_USERNAME);

        when(messageService.deleteMessage(any(DeleteMessageRequest.class))).thenReturn(deletedMessage);
        when(sessionManager.isUserOnline(RECIPIENT_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(RECIPIENT_USERNAME)).thenReturn(recipientSessionInfo);
        when(recipientSessionInfo.getWsSession()).thenReturn(recipientSession);

        // When
        deleteMessageHandler.handle(sessionContext, message);

        // Then
        verify(messageService).deleteMessage(message.getDeleteMessageRequest());
        verify(recipientSession).sendMessage(any(BinaryMessage.class));
        verify(messageService, never()).savePendingDeletion(anyString(), anyString());
        assertSuccessResponseSent(senderSession);
    }

    @Test
    void handle_HappyPath_RecipientOffline() throws Exception {
        // Given
        WsMessage message = createDeleteRequest(MESSAGE_ID);
        Message deletedMessage = new Message();
        deletedMessage.setToUserId(RECIPIENT_USERNAME);

        when(messageService.deleteMessage(any(DeleteMessageRequest.class))).thenReturn(deletedMessage);
        when(sessionManager.isUserOnline(RECIPIENT_USERNAME)).thenReturn(false);

        // When
        deleteMessageHandler.handle(sessionContext, message);

        // Then
        verify(messageService).deleteMessage(message.getDeleteMessageRequest());
        verify(messageService).savePendingDeletion(RECIPIENT_USERNAME, MESSAGE_ID);
        verify(recipientSession, never()).sendMessage(any(BinaryMessage.class));
        assertSuccessResponseSent(senderSession);
    }

    @Test
    void handle_InvalidContext_ShouldExitEarly() throws Exception {
        // Given
        when(validationUtil.isValidContext(any())).thenReturn(false);
        WsMessage message = createDeleteRequest(MESSAGE_ID);

        // When
        deleteMessageHandler.handle(sessionContext, message);

        // Then
        verifyNoInteractions(messageService, sessionManager);
        verify(senderSession, never()).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void handle_MissingMessageId_ShouldSendErrorResponse() throws Exception {
        // Given
        when(validationUtil.isValidString(eq(""), anyString())).thenReturn(false);
        WsMessage message = createDeleteRequest("");

        // When
        deleteMessageHandler.handle(sessionContext, message);

        // Then
        verify(messageService, never()).deleteMessage(any());
        assertErrorResponseSent(senderSession, "ID de mensaje inválido");
    }

    @Test
    void handle_MessageNotFound_ShouldSendErrorResponse() throws Exception {
        // Given
        WsMessage message = createDeleteRequest(MESSAGE_ID);
        when(messageService.deleteMessage(any(DeleteMessageRequest.class))).thenReturn(null);

        // When
        deleteMessageHandler.handle(sessionContext, message);

        // Then
        verify(sessionManager, never()).isUserOnline(anyString());
        assertErrorResponseSent(senderSession, "Mensaje no encontrado");
    }

    @Test
    void handle_DatabaseDeletionFails_ShouldSendErrorResponse() throws Exception {
        // Given
        WsMessage message = createDeleteRequest(MESSAGE_ID);
        when(messageService.deleteMessage(any(DeleteMessageRequest.class)))
                .thenThrow(new RuntimeException("Error al eliminar mensaje de BD"));

        // When
        deleteMessageHandler.handle(sessionContext, message);

        // Then
        verify(sessionManager, never()).isUserOnline(anyString());
        assertErrorResponseSent(senderSession, "Error al eliminar mensaje: Error al eliminar mensaje de BD");
    }

    @Test
    void handle_RealtimeNotificationFails_ShouldFallbackToPending() throws Exception {
        // Given
        WsMessage message = createDeleteRequest(MESSAGE_ID);
        Message deletedMessage = new Message();
        deletedMessage.setToUserId(RECIPIENT_USERNAME);

        when(messageService.deleteMessage(any(DeleteMessageRequest.class))).thenReturn(deletedMessage);
        when(sessionManager.isUserOnline(RECIPIENT_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(RECIPIENT_USERNAME)).thenReturn(null); // Falla la entrega

        // When
        deleteMessageHandler.handle(sessionContext, message);

        // Then
        verify(messageService).savePendingDeletion(RECIPIENT_USERNAME, MESSAGE_ID);
        assertSuccessResponseSent(senderSession);
    }

    private WsMessage createDeleteRequest(String messageId) {
        return WsMessage.newBuilder()
                .setDeleteMessageRequest(DeleteMessageRequest.newBuilder().setMessageId(messageId))
                .build();
    }

    private void assertSuccessResponseSent(WebSocketSession session) throws Exception {
        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(session).sendMessage(captor.capture());
        WsMessage response = WsMessage.parseFrom(captor.getValue().getPayload().array());
        assertTrue(response.getDeleteMessageResponse().getSuccess());
    }

    private void assertErrorResponseSent(WebSocketSession session, String expectedMessage) throws Exception {
        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(session).sendMessage(captor.capture());
        WsMessage response = WsMessage.parseFrom(captor.getValue().getPayload().array());
        assertFalse(response.getDeleteMessageResponse().getSuccess());
        assertEquals(expectedMessage, response.getDeleteMessageResponse().getMessage());
    }
}