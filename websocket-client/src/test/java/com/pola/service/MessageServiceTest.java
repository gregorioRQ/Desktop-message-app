package com.pola.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pola.model.ChatMessage;
import com.pola.model.Contact;
import com.pola.proto.MessagesProto.WsMessage;
import com.pola.repository.MessageRepository;

import javafx.application.Platform;

@ExtendWith(MockitoExtension.class)
public class MessageServiceTest {

    @Mock
    private WebSocketService webSocketService;
    @Mock
    private ContactService contactService;
    @Mock
    private MessageRepository messageRepository;

    private MessageService messageService;

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Toolkit ya inicializado
        }
    }

    @BeforeEach
    void setUp() {
        messageService = new MessageService(webSocketService, contactService, messageRepository);
        messageService.setCurrentUsername("currentUser");
        messageService.setCurrentUserId("currentId");
    }

    @Nested
    @DisplayName("Pruebas para loadChatHistory")
    class LoadChatHistoryTests {

        @Test
        @DisplayName("Debe cargar mensajes y marcar los no leídos como leídos")
        void testLoadChatHistory_WithUnreadMessages() throws SQLException {
            String contactUsername = "friendUser";
            Contact contact = new Contact("friendId", contactUsername);

            ChatMessage msg1 = new ChatMessage(contactUsername, "currentUser", "Hola", "currentId");
            msg1.setId(100L);
            msg1.setStatus(ChatMessage.MessageStatus.READ);

            ChatMessage msg2 = new ChatMessage(contactUsername, "currentUser", "Cómo estás?", "currentId");
            msg2.setId(101L);
            msg2.setStatus(ChatMessage.MessageStatus.DELIVERED);

            List<ChatMessage> history = Arrays.asList(msg1, msg2);
            List<Long> unreadIds = Collections.singletonList(101L);

when(messageRepository.findByContactUsername(contactUsername)).thenReturn(history);
            when(messageRepository.getMessageIdsByStatus(eq(contactUsername), any())).thenReturn(unreadIds);
            when(webSocketService.isConnected()).thenReturn(true);

            messageService.loadChatHistory(contact);

            assertEquals(2, messageService.getMessages().size());
            verify(messageRepository).updateMultipleStatus(unreadIds, ChatMessage.MessageStatus.READ);
            verify(webSocketService).sendMessage(any(WsMessage.class));
            assertEquals(ChatMessage.MessageStatus.READ, msg2.getStatus());
        }

        @Test
        @DisplayName("Debe cargar mensajes sin efectos secundarios si todos están leídos")
        void testLoadChatHistory_NoUnreadMessages() throws SQLException {
            String contactUsername = "friendUser";
            Contact contact = new Contact("friendId", contactUsername);
            ChatMessage msg1 = new ChatMessage(contactUsername, "currentUser", "Hola", "currentId");
            msg1.setId(100L);
            msg1.setStatus(ChatMessage.MessageStatus.READ);

            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(Collections.singletonList(msg1));
            when(messageRepository.getMessageIdsByStatus(eq(contactUsername), any())).thenReturn(Collections.emptyList());

            messageService.loadChatHistory(contact);

            assertEquals(1, messageService.getMessages().size());
            verify(messageRepository, never()).updateMultipleStatus(any(), any());
            verify(webSocketService, never()).sendMessage(any());
        }
    }

    @Nested
    @DisplayName("Pruebas para sendTextMessage")
    class SendTextMessageTests {

        @Test
        @DisplayName("No debe enviar mensaje si no hay contacto seleccionado")
        void testSendTextMessage_NoContactSelected() throws SQLException {
            messageService.sendTextMessage("Hola", "currentUser");

            verify(messageRepository, never()).create(any());
            verify(webSocketService, never()).sendMessage(any());
        }

        @Test
        @DisplayName("No debe enviar mensaje si el contenido es inválido")
        void testSendTextMessage_InvalidContent() throws SQLException {
            String contactUsername = "friend";
            Contact contact = new Contact("id", contactUsername);

            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(Collections.emptyList());
            when(messageRepository.getMessageIdsByStatus(eq(contactUsername), any())).thenReturn(Collections.emptyList());

            messageService.loadChatHistory(contact);

            messageService.sendTextMessage("", "currentUser");
            messageService.sendTextMessage(null, "currentUser");
            messageService.sendTextMessage(" ", "currentUser");

            verify(messageRepository, never()).create(any());
            verify(webSocketService, never()).sendMessage(any());
        }

        @Test
        @DisplayName("No debe enviar mensaje si el usuario está bloqueado")
        void testSendTextMessage_BlockedUser() throws SQLException {
            String contactUsername = "blocker";
            Contact contact = new Contact("id", contactUsername);

            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(Collections.emptyList());
            when(messageRepository.getMessageIdsByStatus(eq(contactUsername), any())).thenReturn(Collections.emptyList());
            when(contactService.isUserBlockingMe(contactUsername)).thenReturn(true);

            messageService.loadChatHistory(contact);

            messageService.sendTextMessage("Hola", "currentUser");

            verify(messageRepository, never()).create(any());
            verify(webSocketService, never()).sendMessage(any());
        }

        @Test
        @DisplayName("Debe enviar mensaje correctamente (Happy Path)")
        void testSendTextMessage_Success() throws SQLException {
            String contactUsername = "friend";
            Contact contact = new Contact("id", contactUsername);
            String messageContent = "Hola Mundo";

            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(Collections.emptyList());
            when(messageRepository.getMessageIdsByStatus(eq(contactUsername), any())).thenReturn(Collections.emptyList());
            when(contactService.isUserBlockingMe(contactUsername)).thenReturn(false);
            when(webSocketService.isConnected()).thenReturn(true);
            when(messageRepository.create(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

            messageService.loadChatHistory(contact);

            messageService.sendTextMessage(messageContent, "currentUser");

            verify(messageRepository).create(any(ChatMessage.class));
            verify(webSocketService).sendMessage(any(WsMessage.class));
            assertEquals(1, messageService.getMessages().size());
            assertEquals(messageContent, messageService.getMessages().get(0).getContent());
        }
    }

    @Nested
    @DisplayName("Pruebas para clearChatHistory")
    class ClearChatHistoryTests {

        @Test
        @DisplayName("Debe borrar historial localmente sin enviar al servidor")
        void testClearChatHistory_LocalOnly() throws SQLException {
            String contactUsername = "friend";
            Contact contact = new Contact("id1", contactUsername);
            contact.setId(1);

            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(Collections.emptyList());
            when(messageRepository.getMessageIdsByStatus(eq(contactUsername), any())).thenReturn(Collections.emptyList());
            messageService.loadChatHistory(contact);

            messageService.clearChatHistory(contact, false);

            verify(messageRepository).deleteByContactUsername(contactUsername);
            verify(webSocketService, never()).sendMessage(any());
        }

        @Test
        @DisplayName("Debe borrar historial globalmente enviando petición al servidor")
        void testClearChatHistory_Global() throws SQLException {
            String contactUsername = "friend";
            Contact contact = new Contact("id1", contactUsername);
            contact.setId(1);

            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(Collections.emptyList());
            when(messageRepository.getMessageIdsByStatus(eq(contactUsername), any())).thenReturn(Collections.emptyList());
            when(webSocketService.isConnected()).thenReturn(true);

            messageService.loadChatHistory(contact);

            messageService.clearChatHistory(contact, true);

            verify(messageRepository).deleteByContactUsername(contactUsername);
            verify(webSocketService).sendMessage(any(WsMessage.class));
        }
    }

    @Nested
    @DisplayName("Pruebas para deleteOneMessage")
    class DeleteOneMessageTests {

        @Test
        @DisplayName("Debe eliminar mensaje localmente y notificar al servidor si está conectado")
        void testDeleteOneMessage_Connected() throws SQLException {
            ChatMessage msg = new ChatMessage("friend", "currentUser", "content", "currentId");
            msg.setId(123L);

            messageService.getMessages().add(msg);

            when(webSocketService.isConnected()).thenReturn(true);

            messageService.deleteOneMessage(msg);

            verify(messageRepository).delete(123L);
            verify(webSocketService).sendMessage(any(WsMessage.class));
            assertTrue(messageService.getMessages().isEmpty(), "El mensaje debería haberse eliminado de la lista");
        }

        @Test
        @DisplayName("Debe eliminar mensaje localmente sin notificar si está desconectado")
        void testDeleteOneMessage_Disconnected() throws SQLException {
            ChatMessage msg = new ChatMessage("friend", "currentUser", "content", "currentId");
            msg.setId(456L);
            messageService.getMessages().add(msg);

            when(webSocketService.isConnected()).thenReturn(false);

            messageService.deleteOneMessage(msg);

            verify(messageRepository).delete(456L);
            verify(webSocketService, never()).sendMessage(any());
            assertTrue(messageService.getMessages().isEmpty(), "El mensaje debería haberse eliminado de la lista");
        }
    }
}
