package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.service.BlockService;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ChatMessageHandlerTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private MessageService messageService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Mock
    private BlockService blockService;

    @Mock
    private SessionContext sessionContext;

    @Mock
    private WebSocketSession webSocketSession;

    @Mock
    private SessionManager.SessionInfo recipientSessionInfo;

    @InjectMocks
    private ChatMessageHandler chatMessageHandler;

    private MessagesProto.WsMessage wsMessage;
    private MessagesProto.ChatMessage chatMessage;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        chatMessage = MessagesProto.ChatMessage.newBuilder()
                .setId("1")
                .setSender("user1")
                .setRecipient("user2")
                .setContent("Hello")
                .build();
        wsMessage = MessagesProto.WsMessage.newBuilder().setChatMessage(chatMessage).build();
        when(sessionContext.getSession()).thenReturn(webSocketSession);
    }

    @Test
    void testHandle_SuccessfulDelivery_OnlineUser() throws Exception {
        when(blockService.isBlocked("user1", "user2")).thenReturn(false);
        when(sessionManager.isUserOnline("user2")).thenReturn(true);
        when(sessionManager.findByUsername("user2")).thenReturn(recipientSessionInfo);
        when(recipientSessionInfo.getWsSession()).thenReturn(webSocketSession);

        chatMessageHandler.handle(sessionContext, wsMessage);

        verify(blockService, times(1)).isBlocked("user1", "user2");
        verify(sessionManager, times(1)).isUserOnline("user2");
        verify(webSocketSession, times(1)).sendMessage(any(BinaryMessage.class));
        verify(messageService, times(1)).saveMessage(chatMessage);
        verify(rabbitTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void testHandle_SuccessfulDelivery_OfflineUser() throws Exception {
        when(blockService.isBlocked("user1", "user2")).thenReturn(false);
        when(sessionManager.isUserOnline("user2")).thenReturn(false);

        chatMessageHandler.handle(sessionContext, wsMessage);

        verify(blockService).isBlocked("user1", "user2");
        verify(sessionManager).isUserOnline("user2");
        verify(rabbitTemplate).convertAndSend(eq("message.sent"), any(com.basic_chat.chat_service.models.MessageSentEvent.class));
        verify(messageService).saveMessage(chatMessage);
        verify(webSocketSession, never()).sendMessage(any(BinaryMessage.class));
    }

    @Test
    void testHandle_BlockedMessage() throws Exception {
        when(blockService.isBlocked("user1", "user2")).thenReturn(true);

        chatMessageHandler.handle(sessionContext, wsMessage);

        verify(blockService).isBlocked("user1", "user2");
        verify(webSocketSession).sendMessage(any(BinaryMessage.class)); // For block response
        verify(messageService, never()).saveMessage(any());
        verify(rabbitTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void testHandle_ExceptionInBlockService() throws Exception {
        when(blockService.isBlocked(anyString(), anyString())).thenThrow(new RuntimeException("DB error"));

        chatMessageHandler.handle(sessionContext, wsMessage);

        // Should not block message flow, logs error and continues
        verify(sessionManager).isUserOnline(anyString());
        verify(messageService).saveMessage(any());
    }

    @Test
    void testHandle_ExceptionInMessageDelivery() throws Exception {
        when(blockService.isBlocked("user1", "user2")).thenReturn(false);
        when(sessionManager.isUserOnline("user2")).thenReturn(true);
        when(sessionManager.findByUsername("user2")).thenReturn(recipientSessionInfo);
        when(recipientSessionInfo.getWsSession()).thenReturn(webSocketSession);
        doThrow(new RuntimeException("Network error")).when(webSocketSession).sendMessage(any(BinaryMessage.class));

        chatMessageHandler.handle(sessionContext, wsMessage);

        // Continues to save message even if delivery fails
        verify(messageService).saveMessage(chatMessage);
    }

    @Test
    void testHandle_ExceptionInSaveMessage() throws Exception {
        when(blockService.isBlocked("user1", "user2")).thenReturn(false);
        when(sessionManager.isUserOnline("user2")).thenReturn(true);
        when(sessionManager.findByUsername("user2")).thenReturn(recipientSessionInfo);
        when(recipientSessionInfo.getWsSession()).thenReturn(webSocketSession);
        doThrow(new RuntimeException("DB error")).when(messageService).saveMessage(any());

        chatMessageHandler.handle(sessionContext, wsMessage);

        // All previous steps should be executed
        verify(blockService).isBlocked("user1", "user2");
        verify(webSocketSession).sendMessage(any(BinaryMessage.class));
        verify(messageService).saveMessage(chatMessage); // This is the one that fails
    }

    @Test
    void testSupports_ChatMessage() {
        assertTrue(chatMessageHandler.supports(wsMessage));
    }

    @Test
    void testSupports_OtherMessage() {
        MessagesProto.WsMessage otherMessage = MessagesProto.WsMessage.newBuilder().build();
        assertFalse(chatMessageHandler.supports(otherMessage));
    }
}
