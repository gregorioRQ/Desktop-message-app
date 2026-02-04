package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.chat_service.util.WebSocketValidationUtil;
import com.basic_chat.proto.MessagesProto.MarkMessagesAsReadRequest;
import com.basic_chat.proto.MessagesProto.MessagesReadUpdate;
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

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MarkAsReadHandlerTest {

    @Mock
    private MessageService messageService;

    @Mock
    private SessionManager sessionManager;

    @Mock
    private WebSocketValidationUtil validationUtil;

    @Mock
    private SessionContext sessionContext;

    @Mock
    private WebSocketSession readerSession;

    @Mock
    private WebSocketSession senderSession;

    @Mock
    private SessionManager.SessionInfo readerSessionInfo;

    @Mock
    private SessionManager.SessionInfo senderSessionInfo;

    @InjectMocks
    private MarkAsReadHandler markAsReadHandler;

    private static final String READER_USERNAME = "reader";
    private static final String SENDER_USERNAME = "sender";
    private static final String READER_SESSION_ID = "reader-s1";
    private static final List<String> MESSAGE_IDS = List.of("msg-1", "msg-2");

    @BeforeEach
    void setUp() {
        // Configuración por defecto para validaciones exitosas
        lenient().when(validationUtil.isValidContext(any())).thenReturn(true);
        lenient().when(validationUtil.isValidProtobufField(anyBoolean(), anyString())).thenReturn(true);
        lenient().when(validationUtil.isValidList(any(), anyString())).thenReturn(true);
        lenient().when(validationUtil.isValidString(anyString(), anyString())).thenReturn(true);
        lenient().when(validationUtil.isValidWebSocketSession(any())).thenReturn(true);
        lenient().when(validationUtil.isValidStrings(anyString(), anyString(), anyString(), anyString())).thenReturn(true);

        // Configuración del contexto y sesión del lector
        lenient().when(sessionContext.getSession()).thenReturn(readerSession);
        lenient().when(readerSession.getId()).thenReturn(READER_SESSION_ID);
        lenient().when(sessionManager.getSessionInfo(READER_SESSION_ID)).thenReturn(readerSessionInfo);
        lenient().when(readerSessionInfo.getUsername()).thenReturn(READER_USERNAME);
    }

    @Test
    void supports_ShouldReturnTrueForMarkMessagesAsReadRequest() {
        WsMessage message = WsMessage.newBuilder()
                .setMarkMessagesAsReadRequest(MarkMessagesAsReadRequest.getDefaultInstance())
                .build();
        assertTrue(markAsReadHandler.supports(message));
    }

    @Test
    void handle_HappyPath_SenderOnline() throws Exception {
        // Given
        WsMessage requestMessage = createMarkAsReadRequest(MESSAGE_IDS);
        List<Message> updatedMessages = createUpdatedMessages(MESSAGE_IDS, SENDER_USERNAME);

        when(messageService.markMessagesAsRead(MESSAGE_IDS, READER_USERNAME)).thenReturn(updatedMessages);
        when(sessionManager.isUserOnline(SENDER_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(SENDER_USERNAME)).thenReturn(senderSessionInfo);
        when(senderSessionInfo.getWsSession()).thenReturn(senderSession);

        // When
        markAsReadHandler.handle(sessionContext, requestMessage);

        // Then
        verify(messageService).markMessagesAsRead(MESSAGE_IDS, READER_USERNAME);

        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(senderSession).sendMessage(captor.capture());
        WsMessage sentMessage = WsMessage.parseFrom(captor.getValue().getPayload().array());
        assertTrue(sentMessage.hasMessagesReadUpdate());
        MessagesReadUpdate update = sentMessage.getMessagesReadUpdate();
        assertEquals(READER_USERNAME, update.getReaderUsername());
        assertEquals(MESSAGE_IDS, update.getMessageIdsList());

        verify(messageService, never()).savePendingReadReceipts(anyString(), any(), anyString());
    }

    @Test
    void handle_HappyPath_SenderOffline() throws Exception {
        // Given
        WsMessage requestMessage = createMarkAsReadRequest(MESSAGE_IDS);
        List<Message> updatedMessages = createUpdatedMessages(MESSAGE_IDS, SENDER_USERNAME);

        when(messageService.markMessagesAsRead(MESSAGE_IDS, READER_USERNAME)).thenReturn(updatedMessages);
        when(sessionManager.isUserOnline(SENDER_USERNAME)).thenReturn(false);

        // When
        markAsReadHandler.handle(sessionContext, requestMessage);

        // Then
        verify(messageService).markMessagesAsRead(MESSAGE_IDS, READER_USERNAME);
        verify(messageService).savePendingReadReceipts(SENDER_USERNAME, MESSAGE_IDS, READER_USERNAME);
        verify(senderSession, never()).sendMessage(any());
    }

    @Test
    void handle_InvalidContext_ShouldExitEarly() throws Exception {
        // Given
        when(validationUtil.isValidContext(any())).thenReturn(false);
        WsMessage requestMessage = createMarkAsReadRequest(MESSAGE_IDS);

        // When
        markAsReadHandler.handle(sessionContext, requestMessage);

        // Then
        verifyNoInteractions(messageService, sessionManager);
    }

    @Test
    void handle_EmptyMessageIdList_ShouldExitEarly() throws Exception {
        // Given
        when(validationUtil.isValidList(eq(Collections.emptyList()), anyString())).thenReturn(false);
        WsMessage requestMessage = createMarkAsReadRequest(Collections.emptyList());

        // When
        markAsReadHandler.handle(sessionContext, requestMessage);

        // Then
        verifyNoInteractions(messageService);
        verify(sessionManager, never()).isUserOnline(anyString());
    }

    @Test
    void handle_ReaderSessionInfoNotFound_ShouldExitEarly() throws Exception {
        // Given
        when(sessionManager.getSessionInfo(READER_SESSION_ID)).thenReturn(null);
        WsMessage requestMessage = createMarkAsReadRequest(MESSAGE_IDS);

        // When
        markAsReadHandler.handle(sessionContext, requestMessage);

        // Then
        verifyNoInteractions(messageService);
    }

    @Test
    void handle_RealtimeDeliveryFails_ShouldFallbackToPending() throws Exception {
        // Given
        WsMessage requestMessage = createMarkAsReadRequest(MESSAGE_IDS);
        List<Message> updatedMessages = createUpdatedMessages(MESSAGE_IDS, SENDER_USERNAME);

        when(messageService.markMessagesAsRead(MESSAGE_IDS, READER_USERNAME)).thenReturn(updatedMessages);
        when(sessionManager.isUserOnline(SENDER_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(SENDER_USERNAME)).thenReturn(senderSessionInfo);
        when(senderSessionInfo.getWsSession()).thenReturn(senderSession);
        doThrow(new IOException("Network error")).when(senderSession).sendMessage(any(BinaryMessage.class));

        // When
        markAsReadHandler.handle(sessionContext, requestMessage);

        // Then
        verify(messageService).savePendingReadReceipts(SENDER_USERNAME, MESSAGE_IDS, READER_USERNAME);
    }

    private WsMessage createMarkAsReadRequest(List<String> messageIds) {
        return WsMessage.newBuilder()
                .setMarkMessagesAsReadRequest(MarkMessagesAsReadRequest.newBuilder()
                        .addAllMessageIds(messageIds))
                .build();
    }

    private List<Message> createUpdatedMessages(List<String> messageIds, String fromUser) {
        return messageIds.stream().map(id -> {
            Message msg = new Message();
            // Asumimos que el ID del mensaje es un Long para el modelo
            msg.setId(Long.parseLong(id.replace("msg-", "")));
            msg.setFromUserId(fromUser);
            return msg;
        }).toList();
    }
}