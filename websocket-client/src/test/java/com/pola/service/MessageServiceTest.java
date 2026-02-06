package com.pola.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    // Inicializar JavaFX Toolkit para soportar Platform.runLater
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
        // Usamos el constructor que permite inyectar el repositorio mockeado
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
            // Arrange
            String contactUsername = "friendUser";
            Contact contact = new Contact("friendId", contactUsername, null);
            
            // Mensaje 1: Ya leído
            ChatMessage msg1 = new ChatMessage(contactUsername, "Hola", contactUsername);
            msg1.setId(100L);
            msg1.setRead(true);
            
            // Mensaje 2: No leído
            ChatMessage msg2 = new ChatMessage(contactUsername, "Cómo estás?", contactUsername);
            msg2.setId(101L);
            msg2.setRead(false);

            List<ChatMessage> history = Arrays.asList(msg1, msg2);
            List<Long> unreadIds = Collections.singletonList(101L);

            // Configurar comportamiento de los mocks
            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(history);
            when(messageRepository.getUnreadMessageIds(contactUsername)).thenReturn(unreadIds);
            when(webSocketService.isConnected()).thenReturn(true);

            // Act
            messageService.loadChatHistory(contact);

            // Assert
            assertEquals(2, messageService.getMessages().size());
            verify(messageRepository).markMultipleAsRead(unreadIds);
            verify(webSocketService).sendMessage(any(WsMessage.class));
            assertTrue(msg2.isRead(), "El mensaje local debería haberse marcado como leído");
        }

        @Test
        @DisplayName("Debe cargar mensajes sin efectos secundarios si todos están leídos")
        void testLoadChatHistory_NoUnreadMessages() throws SQLException {
            // Arrange
            String contactUsername = "friendUser";
            Contact contact = new Contact("friendId", contactUsername, null);
            ChatMessage msg1 = new ChatMessage(contactUsername, "Hola", contactUsername);
            msg1.setId(100L);
            
            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(Collections.singletonList(msg1));
            when(messageRepository.getUnreadMessageIds(contactUsername)).thenReturn(Collections.emptyList());

            // Act
            messageService.loadChatHistory(contact);

            // Assert
            assertEquals(1, messageService.getMessages().size());
            verify(messageRepository, never()).markMultipleAsRead(any());
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
            // Arrange
            String contactUsername = "friend";
            Contact contact = new Contact("id", contactUsername, null);
            
            // Mocks necesarios para loadChatHistory (que establece el contacto actual)
            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(Collections.emptyList());
            when(messageRepository.getUnreadMessageIds(contactUsername)).thenReturn(Collections.emptyList());
            
            messageService.loadChatHistory(contact);

            // Act & Assert
            messageService.sendTextMessage("", "currentUser");
            messageService.sendTextMessage(null, "currentUser");
            messageService.sendTextMessage("   ", "currentUser");

            verify(messageRepository, never()).create(any());
            verify(webSocketService, never()).sendMessage(any());
        }

        @Test
        @DisplayName("No debe enviar mensaje si el usuario está bloqueado")
        void testSendTextMessage_BlockedUser() throws SQLException {
            // Arrange
            String contactUsername = "blocker";
            Contact contact = new Contact("id", contactUsername, null);
            
            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(Collections.emptyList());
            when(messageRepository.getUnreadMessageIds(contactUsername)).thenReturn(Collections.emptyList());
            when(contactService.isUserBlockingMe(contactUsername)).thenReturn(true);

            messageService.loadChatHistory(contact);

            // Act
            messageService.sendTextMessage("Hola", "currentUser");

            // Assert
            verify(messageRepository, never()).create(any());
            verify(webSocketService, never()).sendMessage(any());
        }

        @Test
        @DisplayName("Debe enviar mensaje correctamente (Happy Path)")
        void testSendTextMessage_Success() throws SQLException {
            // Arrange
            String contactUsername = "friend";
            Contact contact = new Contact("id", contactUsername, null);
            String messageContent = "Hola Mundo";
            
            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(Collections.emptyList());
            when(messageRepository.getUnreadMessageIds(contactUsername)).thenReturn(Collections.emptyList());
            when(contactService.isUserBlockingMe(contactUsername)).thenReturn(false);
            when(webSocketService.isConnected()).thenReturn(true);
            // Simular que el repositorio devuelve el mensaje guardado
            when(messageRepository.create(any(ChatMessage.class))).thenAnswer(i -> i.getArgument(0));

            messageService.loadChatHistory(contact);

            // Act
            messageService.sendTextMessage(messageContent, "currentUser");

            // Assert
            // 1. Verificar persistencia
            verify(messageRepository).create(any(ChatMessage.class));
            
            // 2. Verificar envío por WebSocket
            verify(webSocketService).sendMessage(any(WsMessage.class));
            
            // 3. Verificar actualización de UI
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
            // Arrange
            String contactUsername = "friend";
            Contact contact = new Contact("id1", contactUsername, null);
            contact.setId(1);
            
            // Simular que este es el contacto actual
            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(Collections.emptyList());
            when(messageRepository.getUnreadMessageIds(contactUsername)).thenReturn(Collections.emptyList());
            messageService.loadChatHistory(contact);
            
            // Act
            messageService.clearChatHistory(contact, false);

            // Assert
            verify(messageRepository).deleteByContactUsername(contactUsername);
            verify(webSocketService, never()).sendMessage(any());
        }

        @Test
        @DisplayName("Debe borrar historial globalmente enviando petición al servidor")
        void testClearChatHistory_Global() throws SQLException {
            // Arrange
            String contactUsername = "friend";
            Contact contact = new Contact("id1", contactUsername, null);
            contact.setId(1);
            
            when(messageRepository.findByContactUsername(contactUsername)).thenReturn(Collections.emptyList());
            when(messageRepository.getUnreadMessageIds(contactUsername)).thenReturn(Collections.emptyList());
            when(webSocketService.isConnected()).thenReturn(true);
            
            messageService.loadChatHistory(contact);

            // Act
            messageService.clearChatHistory(contact, true);

            // Assert
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
            // Arrange
            ChatMessage msg = new ChatMessage("friend", "content", "me");
            msg.setId(123L);
            
            // Añadimos el mensaje a la lista observable para verificar que se elimina
            messageService.getMessages().add(msg);

            when(webSocketService.isConnected()).thenReturn(true);

            // Act
            messageService.deleteOneMessage(msg);

            // Assert
            verify(messageRepository).delete(123L);
            verify(webSocketService).sendMessage(any(WsMessage.class));
            assertTrue(messageService.getMessages().isEmpty(), "El mensaje debería haberse eliminado de la lista");
        }

        @Test
        @DisplayName("Debe eliminar mensaje localmente sin notificar si está desconectado")
        void testDeleteOneMessage_Disconnected() throws SQLException {
            // Arrange
            ChatMessage msg = new ChatMessage("friend", "content", "me");
            msg.setId(456L);
            messageService.getMessages().add(msg);

            when(webSocketService.isConnected()).thenReturn(false);

            // Act
            messageService.deleteOneMessage(msg);

            // Assert
            verify(messageRepository).delete(456L);
            verify(webSocketService, never()).sendMessage(any());
            assertTrue(messageService.getMessages().isEmpty(), "El mensaje debería haberse eliminado de la lista");
        }
    }
}