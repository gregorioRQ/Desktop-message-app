package com.pola.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.pola.model.ChatMessage;
import com.pola.model.Notification;
import com.pola.repository.MessageRepository;
import com.pola.util.MessageProcessingContext;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas para IncomingMessageProcessor - Read Receipt Debounce")
public class IncomingMessageProcessorReadReceiptTest {

    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ContactService contactService;
    @Mock
    private MessageSender messageSender;
    @Mock
    private ScheduledExecutorService scheduler;
    @SuppressWarnings("rawtypes")
    @Mock
    private ScheduledFuture scheduledFuture;

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

        processor = new IncomingMessageProcessor(context, scheduler);
    }

    @Nested
    @DisplayName("Pruebas para scheduleReadReceipt")
    class ScheduleReadReceiptTests {

        @Test
        @DisplayName("Debe crear un nuevo timer cuando no existe timer anterior para el contacto")
        void testScheduleReadReceipt_NewTimer() {
            String senderId = "friendUser";

            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(scheduledFuture);

            processor.scheduleReadReceipt(senderId);

            verify(scheduler).schedule(any(Runnable.class), eq(3000L), eq(TimeUnit.MILLISECONDS));
            assertTrue(processor.getReadReceiptTimers().containsKey(senderId));
        }

        @Test
        @DisplayName("Debe cancelar timer existente cuando llega nuevo mensaje del mismo contacto")
        void testScheduleReadReceipt_ResetTimer() {
            String senderId = "friendUser";

            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(scheduledFuture);

            processor.scheduleReadReceipt(senderId);
            processor.scheduleReadReceipt(senderId);

            verify(scheduledFuture).cancel(false);
            verify(scheduler, times(2)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("Debe crear timer para diferentes contactos sin cancelar otros")
        void testScheduleReadReceipt_DifferentContacts() {
            String sender1 = "friendUser1";
            String sender2 = "friendUser2";

            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(scheduledFuture);

            processor.scheduleReadReceipt(sender1);
            processor.scheduleReadReceipt(sender2);

            verify(scheduler, times(2)).schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS));
            assertEquals(2, processor.getReadReceiptTimers().size());
        }
    }

    @Nested
    @DisplayName("Pruebas para executeReadReceipt")
    class ExecuteReadReceiptTests {

        @Test
        @DisplayName("Debe obtener IDs de mensajes no leídos y enviarlos al servidor")
        void testExecuteReadReceipt_WithUnreadMessages() throws SQLException {
            String senderId = "friendUser";
            List<Long> unreadIds = Arrays.asList(100L, 101L, 102L);

            when(messageRepository.getMessageIdsByStatus(eq(senderId), any())).thenReturn(unreadIds);
            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(scheduledFuture);

            processor.scheduleReadReceipt(senderId);

            ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).schedule(runnableCaptor.capture(), eq(3000L), eq(TimeUnit.MILLISECONDS));

            Runnable task = runnableCaptor.getValue();
            assertNotNull(task);
            task.run();

            verify(messageRepository).getMessageIdsByStatus(eq(senderId), any());
            verify(messageRepository).updateMultipleStatus(unreadIds, ChatMessage.MessageStatus.READ);
            verify(messageSender).sendMarkAsRead("currentUserId", senderId, unreadIds);
        }

        @Test
        @DisplayName("Debe limpiar timer después de ejecutar cuando hay mensajes")
        void testExecuteReadReceipt_ClearTimerAfterExecution() throws SQLException {
            String senderId = "friendUser";
            List<Long> unreadIds = Arrays.asList(100L);

            when(messageRepository.getMessageIdsByStatus(eq(senderId), any())).thenReturn(unreadIds);
            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(scheduledFuture);

            processor.scheduleReadReceipt(senderId);

            ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).schedule(runnableCaptor.capture(), eq(3000L), eq(TimeUnit.MILLISECONDS));
            runnableCaptor.getValue().run();

            assertFalse(processor.getReadReceiptTimers().containsKey(senderId));
        }

        @Test
        @DisplayName("Debe no enviar nada cuando no hay mensajes sin leer")
        void testExecuteReadReceipt_NoUnreadMessages() throws SQLException {
            String senderId = "friendUser";

            when(messageRepository.getMessageIdsByStatus(eq(senderId), any())).thenReturn(Collections.emptyList());
            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(scheduledFuture);

            processor.scheduleReadReceipt(senderId);

            ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).schedule(runnableCaptor.capture(), eq(3000L), eq(TimeUnit.MILLISECONDS));
            runnableCaptor.getValue().run();

            verify(messageRepository, never()).updateMultipleStatus(anyList(), any());
            verify(messageSender, never()).sendMarkAsRead(any(), any(), anyList());
            assertFalse(processor.getReadReceiptTimers().containsKey(senderId));
        }

        @Test
        @DisplayName("Debe manejar SQLException al ejecutar confirmación de lectura")
        void testExecuteReadReceipt_SqlException() throws SQLException {
            String senderId = "friendUser";

            when(messageRepository.getMessageIdsByStatus(eq(senderId), any())).thenThrow(new SQLException("DB Error"));
            when(scheduler.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                    .thenReturn(scheduledFuture);

            processor.scheduleReadReceipt(senderId);

            ArgumentCaptor<Runnable> runnableCaptor = ArgumentCaptor.forClass(Runnable.class);
            verify(scheduler).schedule(runnableCaptor.capture(), eq(3000L), eq(TimeUnit.MILLISECONDS));
            runnableCaptor.getValue().run();

            assertFalse(processor.getReadReceiptTimers().containsKey(senderId));
        }
    }
}
