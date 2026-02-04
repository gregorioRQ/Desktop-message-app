package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.PendingContactIdentity;
import com.basic_chat.chat_service.repository.PendingContactIdentityRepository;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto.ContactIdentity;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContactIdentityHandlerTest {

    @Mock
    private SessionManager sessionManager;

    @Mock
    private PendingContactIdentityRepository pendingContactIdentityRepository;

    @Mock
    private SessionContext sessionContext;

    @Mock
    private WebSocketSession senderSession;

    @Mock
    private WebSocketSession recipientSession;

    @Mock
    private SessionManager.SessionInfo recipientSessionInfo;

    @InjectMocks
    private ContactIdentityHandler contactIdentityHandler;

    private static final String SENDER_ID = "uuid-sender";
    private static final String SENDER_USERNAME = "sender";
    private static final String RECIPIENT_USERNAME = "recipient";

    @BeforeEach
    void setUp() {
        lenient().when(sessionContext.getSession()).thenReturn(senderSession);
    }

    @Test
    void supports_ShouldReturnTrueForContactIdentity() {
        WsMessage message = WsMessage.newBuilder()
                .setContactIdentity(ContactIdentity.getDefaultInstance())
                .build();
        assertTrue(contactIdentityHandler.supports(message));
    }

    @Test
    void handle_HappyPath_RecipientOnline() throws Exception {
        // Given
        WsMessage message = createContactIdentityMessage(RECIPIENT_USERNAME, SENDER_ID, SENDER_USERNAME);
        when(sessionManager.isUserOnline(RECIPIENT_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(RECIPIENT_USERNAME)).thenReturn(recipientSessionInfo);
        when(recipientSessionInfo.getWsSession()).thenReturn(recipientSession);
        when(recipientSession.isOpen()).thenReturn(true);

        // When
        contactIdentityHandler.handle(sessionContext, message);

        // Then
        verify(recipientSession).sendMessage(any(BinaryMessage.class));
        verify(pendingContactIdentityRepository, never()).save(any());
    }

    @Test
    void handle_HappyPath_RecipientOffline() throws Exception {
        // Given
        WsMessage message = createContactIdentityMessage(RECIPIENT_USERNAME, SENDER_ID, SENDER_USERNAME);
        when(sessionManager.isUserOnline(RECIPIENT_USERNAME)).thenReturn(false);

        // When
        contactIdentityHandler.handle(sessionContext, message);

        // Then
        ArgumentCaptor<PendingContactIdentity> captor = ArgumentCaptor.forClass(PendingContactIdentity.class);
        verify(pendingContactIdentityRepository).save(captor.capture());
        assertEquals(RECIPIENT_USERNAME, captor.getValue().getRecipient());
        assertEquals(SENDER_ID, captor.getValue().getSenderId());
        assertEquals(SENDER_USERNAME, captor.getValue().getSenderUsername());
        verify(sessionManager, never()).findByUsername(anyString());
    }

    @Test
    void handle_InvalidIdentity_MissingRecipient() throws Exception {
        // Given
        WsMessage message = createContactIdentityMessage("", SENDER_ID, SENDER_USERNAME);

        // When
        contactIdentityHandler.handle(sessionContext, message);

        // Then
        verifyNoInteractions(sessionManager, pendingContactIdentityRepository);
    }

    @Test
    void handle_InvalidIdentity_SameUser() throws Exception {
        // Given
        WsMessage message = createContactIdentityMessage(SENDER_USERNAME, SENDER_ID, SENDER_USERNAME);

        // When
        contactIdentityHandler.handle(sessionContext, message);

        // Then
        verifyNoInteractions(sessionManager, pendingContactIdentityRepository);
    }

    @Test
    void handle_RecipientOnline_ButSessionInfoNull_ShouldSavePending() throws Exception {
        // Given
        WsMessage message = createContactIdentityMessage(RECIPIENT_USERNAME, SENDER_ID, SENDER_USERNAME);
        when(sessionManager.isUserOnline(RECIPIENT_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(RECIPIENT_USERNAME)).thenReturn(null);

        // When
        contactIdentityHandler.handle(sessionContext, message);

        // Then
        verify(pendingContactIdentityRepository).save(any(PendingContactIdentity.class));
        verify(recipientSession, never()).sendMessage(any());
    }

    @Test
    void handle_RecipientOnline_ButSessionClosed_ShouldSavePending() throws Exception {
        // Given
        WsMessage message = createContactIdentityMessage(RECIPIENT_USERNAME, SENDER_ID, SENDER_USERNAME);
        when(sessionManager.isUserOnline(RECIPIENT_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(RECIPIENT_USERNAME)).thenReturn(recipientSessionInfo);
        when(recipientSessionInfo.getWsSession()).thenReturn(recipientSession);
        when(recipientSession.isOpen()).thenReturn(false);

        // When
        contactIdentityHandler.handle(sessionContext, message);

        // Then
        verify(pendingContactIdentityRepository).save(any(PendingContactIdentity.class));
        verify(recipientSession, never()).sendMessage(any());
    }

    @Test
    void handle_RealtimeDeliveryFails_ShouldSavePending() throws Exception {
        // Given
        WsMessage message = createContactIdentityMessage(RECIPIENT_USERNAME, SENDER_ID, SENDER_USERNAME);
        when(sessionManager.isUserOnline(RECIPIENT_USERNAME)).thenReturn(true);
        when(sessionManager.findByUsername(RECIPIENT_USERNAME)).thenReturn(recipientSessionInfo);
        when(recipientSessionInfo.getWsSession()).thenReturn(recipientSession);
        when(recipientSession.isOpen()).thenReturn(true);
        doThrow(new IOException("Network error")).when(recipientSession).sendMessage(any(BinaryMessage.class));

        // When
        contactIdentityHandler.handle(sessionContext, message);

        // Then
        verify(pendingContactIdentityRepository).save(any(PendingContactIdentity.class));
    }

    private WsMessage createContactIdentityMessage(String recipient, String senderId, String senderUsername) {
        ContactIdentity identity = ContactIdentity.newBuilder()
                .setContactUsername(recipient)
                .setSenderId(senderId)
                .setSenderUsername(senderUsername)
                .build();
        return WsMessage.newBuilder().setContactIdentity(identity).build();
    }
}