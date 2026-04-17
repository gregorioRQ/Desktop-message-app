package com.pola.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pola.model.ChatMessage;
import com.pola.model.Notification;
import com.pola.proto.MessagesProto.MessagesReadUpdate;
import com.pola.proto.MessagesProto.WsMessage;
import com.pola.repository.MessageRepository;
import com.pola.util.MessageProcessingContext;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Tests unitarios para IncomingMessageProcessor.processMessagesReadUpdate().
 * 
 * Este método se ejecuta cuando el emisor original recibe la confirmación
 * de que sus mensajes fueron leídos por el destinatario.
 * 
 * Flujo:
 * Cliente (uB) marca como leído → connection-service → connection-service → 
 * Cliente (uA) recibe MessagesReadUpdate → processMessagesReadUpdate() → 
 * Marca mensajes como leídos en DB local y UI
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IncomingMessageProcessor - processMessagesReadUpdate Tests")
class IncomingMessageProcessorMessagesReadUpdateTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ContactService contactService;
    @Mock
    private MessageSender messageSender;

    private ObservableList<ChatMessage> currentChatMessages;
    private ObservableList<Notification> notifications;
    private MessageProcessingContext context;
    private IncomingMessageProcessor processor;

    @BeforeEach
    void setUp() throws Exception {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException e) {
            // Already started
        }

        currentChatMessages = FXCollections.observableArrayList();
        notifications = FXCollections.observableArrayList();

        context = new MessageProcessingContext(
                messageRepository,
                contactService,
                messageSender,
                currentChatMessages,
                notifications,
                () -> null,
                () -> "currentUserId",
                () -> "currentUsername"
        );

        processor = new IncomingMessageProcessor(context);
    }

    @Nested
    @DisplayName("Tests para processMessagesReadUpdate()")
    class ProcessMessagesReadUpdateTests {

        @Test
        @DisplayName("Debe marcar mensajes como leídos en el repositorio")
        void testProcessMessagesReadUpdate_UpdatesRepository() throws SQLException {
            List<String> ids = Arrays.asList("100", "101", "102");
            MessagesReadUpdate readUpdate = MessagesReadUpdate.newBuilder()
                    .addAllMessageIds(ids)
                    .setReaderUsername("readerUser")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMessagesReadUpdate(readUpdate)
                    .build();

            processor.process(message);

            verify(messageRepository).markMultipleAsRead(Arrays.asList(100L, 101L, 102L));
        }

        @Test
        @DisplayName("Debe actualizar el estado de los mensajes en la UI")
        void testProcessMessagesReadUpdate_UpdatesUI() throws Exception {
            ChatMessage msg1 = new ChatMessage(100L, "contact", "sender", "Hello", "senderId",
                    LocalDateTime.now(), false, ChatMessage.MessageStatus.DELIVERED);
            ChatMessage msg2 = new ChatMessage(101L, "contact", "sender", "World", "senderId",
                    LocalDateTime.now(), false, ChatMessage.MessageStatus.DELIVERED);
            ChatMessage msg3 = new ChatMessage(102L, "contact", "sender", "Other", "senderId",
                    LocalDateTime.now(), true, ChatMessage.MessageStatus.READ);

            currentChatMessages.addAll(msg1, msg2, msg3);

            MessagesReadUpdate readUpdate = MessagesReadUpdate.newBuilder()
                    .addMessageIds("100")
                    .addMessageIds("101")
                    .setReaderUsername("readerUser")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMessagesReadUpdate(readUpdate)
                    .build();

            processor.process(message);

            Thread.sleep(100);

            assertTrue(msg1.isRead(), "msg1 debería estar marcado como leído");
            assertTrue(msg2.isRead(), "msg2 debería estar marcado como leído");
            assertTrue(msg3.isRead(), "msg3 ya estaba leído");
            assertEquals(ChatMessage.MessageStatus.READ, msg1.getStatus());
            assertEquals(ChatMessage.MessageStatus.READ, msg2.getStatus());
        }

        @Test
        @DisplayName("Debe manejar lista vacía de IDs sin errores")
        void testProcessMessagesReadUpdate_HandlesEmptyIds() throws SQLException {
            MessagesReadUpdate readUpdate = MessagesReadUpdate.newBuilder()
                    .setReaderUsername("readerUser")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMessagesReadUpdate(readUpdate)
                    .build();

            processor.process(message);

            verify(messageRepository, never()).markMultipleAsRead(anyList());
        }

        @Test
        @DisplayName("Debe ignorar IDs con formato inválido")
        void testProcessMessagesReadUpdate_HandlesInvalidIds() throws SQLException {
            MessagesReadUpdate readUpdate = MessagesReadUpdate.newBuilder()
                    .addMessageIds("100")
                    .addMessageIds("invalid")
                    .addMessageIds("101")
                    .setReaderUsername("readerUser")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMessagesReadUpdate(readUpdate)
                    .build();

            processor.process(message);

            verify(messageRepository).markMultipleAsRead(Arrays.asList(100L, 101L));
        }

        @Test
        @DisplayName("Debe manejar SQLException sin lanzar excepción")
        void testProcessMessagesReadUpdate_HandlesSQLException() throws SQLException {
            doThrow(new SQLException("DB Error"))
                    .when(messageRepository).markMultipleAsRead(anyList());

            MessagesReadUpdate readUpdate = MessagesReadUpdate.newBuilder()
                    .addMessageIds("100")
                    .setReaderUsername("readerUser")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMessagesReadUpdate(readUpdate)
                    .build();

            assertDoesNotThrow(() -> processor.process(message));
        }

        @Test
        @DisplayName("Debe llamar a onMessagesUpdated callback")
        void testProcessMessagesReadUpdate_TriggersOnMessagesUpdated() throws Exception {
            Runnable onMessagesUpdated = mock(Runnable.class);
            context.setOnMessagesUpdated(onMessagesUpdated);

            MessagesReadUpdate readUpdate = MessagesReadUpdate.newBuilder()
                    .addMessageIds("100")
                    .setReaderUsername("readerUser")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMessagesReadUpdate(readUpdate)
                    .build();

            processor.process(message);

            Thread.sleep(100);

            verify(onMessagesUpdated).run();
        }

        @Test
        @DisplayName("Debe procesar correctamente un solo ID")
        void testProcessMessagesReadUpdate_SingleId() throws SQLException {
            MessagesReadUpdate readUpdate = MessagesReadUpdate.newBuilder()
                    .addMessageIds("100")
                    .setReaderUsername("readerUser")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMessagesReadUpdate(readUpdate)
                    .build();

            processor.process(message);

            verify(messageRepository).markMultipleAsRead(Collections.singletonList(100L));
        }
    }
}
