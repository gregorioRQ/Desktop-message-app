package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.UserPresenceEvent;
import com.basic_chat.chat_service.security.JwtValidator;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto.AuthMessage;
import com.basic_chat.proto.MessagesProto.WsMessage;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthMessageHandlerTest {

    @Mock
    private JwtValidator jwtValidator;
    @Mock
    private SessionManager sessionManager;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private SessionContext sessionContext;
    @Mock
    private WebSocketSession session;
    @Mock
    private Claims claims;

    @InjectMocks
    private AuthMessageHandler authMessageHandler;

    private static final String SESSION_ID = "s1";
    private static final String TOKEN = "valid.token.jwt";
    private static final String USER_ID = "u1";
    private static final String USERNAME = "user1";

    @BeforeEach
    void setUp() {
        lenient().when(sessionContext.getSession()).thenReturn(session);
        lenient().when(session.getId()).thenReturn(SESSION_ID);
    }

    @Test
    void supports_ShouldReturnTrueForAuthMessage() {
        WsMessage message = WsMessage.newBuilder()
                .setAuthMessage(AuthMessage.getDefaultInstance())
                .build();
        assertTrue(authMessageHandler.supports(message));
    }

    @Test
    void handle_HappyPath() throws Exception {
        // Given
        WsMessage message = createAuthMessage(TOKEN);
        
        when(jwtValidator.validateToken(TOKEN)).thenReturn(claims);
        when(jwtValidator.getUserId(claims)).thenReturn(USER_ID);
        when(jwtValidator.getUsername(claims)).thenReturn(USERNAME);

        // When
        authMessageHandler.handle(sessionContext, message);

        // Then
        verify(sessionManager).authenticateSession(SESSION_ID, USER_ID, USERNAME, session);
        verify(rabbitTemplate).convertAndSend(eq("user.presence"), any(UserPresenceEvent.class));
        
        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(session).sendMessage(captor.capture());
        WsMessage response = WsMessage.parseFrom(captor.getValue().getPayload().array());
        assertTrue(response.getAuthResponse().getSuccess());
        assertEquals(USER_ID, response.getAuthResponse().getUserId());
    }

    @Test
    void handle_EmptyToken_ShouldSendError() throws Exception {
        // Given
        WsMessage message = createAuthMessage("");

        // When
        authMessageHandler.handle(sessionContext, message);

        // Then
        verify(sessionManager, never()).authenticateSession(anyString(), anyString(), anyString(), any());
        
        ArgumentCaptor<BinaryMessage> captor = ArgumentCaptor.forClass(BinaryMessage.class);
        verify(session).sendMessage(captor.capture());
        WsMessage response = WsMessage.parseFrom(captor.getValue().getPayload().array());
        assertFalse(response.getAuthResponse().getSuccess());
        assertEquals("Token requerido", response.getAuthResponse().getMessage());
    }

    @Test
    void handle_InvalidToken_ShouldCloseSession() throws Exception {
        // Given
        WsMessage message = createAuthMessage(TOKEN);
        when(jwtValidator.validateToken(TOKEN)).thenThrow(new RuntimeException("Invalid token"));

        // When
        authMessageHandler.handle(sessionContext, message);

        // Then
        verify(sessionManager, never()).authenticateSession(anyString(), anyString(), anyString(), any());
        verify(session).close();
    }

    private WsMessage createAuthMessage(String token) {
        return WsMessage.newBuilder()
                .setAuthMessage(AuthMessage.newBuilder().setToken(token).build())
                .build();
    }
}