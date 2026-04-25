package com.pola.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

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

import com.pola.proto.MessagesProto.MarkMessagesAsReadRequest;
import com.pola.proto.MessagesProto.WsMessage;

/**
 * Tests unitarios para MessageSender.sendMarkAsRead().
 * 
 * Este método construye y envía mensajes MarkMessagesAsReadRequest al servidor
 * cuando el usuario marca mensajes como leídos.
 * 
 * Flujo:
 * Cliente (uB) → MessageSender.sendMarkAsRead() → WebSocket → 
 * connection-service → destinatario (uA)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessageSender Tests - Read Receipt")
class MessageSenderTest {

    @Mock
    private WebSocketService webSocketService;

    private MessageSender messageSender;

    @BeforeEach
    void setUp() {
        messageSender = new MessageSender(webSocketService);
    }

    @Nested
    @DisplayName("Tests para sendMarkAsRead()")
    class SendMarkAsReadTests {

        @Test
        @DisplayName("Debe construir mensaje con sender, recipient y messageIds correctos")
        void testSendMarkAsRead_BuildsCorrectMessage() {
            when(webSocketService.isConnected()).thenReturn(true);
            
            String sender = "readerUser";
            String recipient = "senderUser";
            List<Long> messageIds = Arrays.asList(100L, 101L, 102L);

            messageSender.sendMarkAsRead(sender, recipient, messageIds);

            verify(webSocketService).sendMessage(argThat((WsMessage msg) -> {
                MarkMessagesAsReadRequest req = msg.getMarkMessagesAsReadRequest();
                return req.getSender().equals(sender)
                        && req.getRecipient().equals(recipient)
                        && req.getMessageIdsList().equals(Arrays.asList("100", "101", "102"));
            }));
        }

        @Test
        @DisplayName("Debe convertir IDs Long a String")
        void testSendMarkAsRead_ConvertsIdsToStrings() {
            when(webSocketService.isConnected()).thenReturn(true);
            
            List<Long> messageIds = Arrays.asList(1L, 999L, 12345L);

            messageSender.sendMarkAsRead("reader", "sender", messageIds);

            verify(webSocketService).sendMessage(argThat((WsMessage msg) -> {
                MarkMessagesAsReadRequest req = msg.getMarkMessagesAsReadRequest();
                return req.getMessageIdsList().equals(Arrays.asList("1", "999", "12345"));
            }));
        }

        @Test
        @DisplayName("Debe llamar sendMessage con WsMessage")
        void testSendMarkAsRead_CallsSendMessage() {
            when(webSocketService.isConnected()).thenReturn(true);
            
            List<Long> messageIds = Arrays.asList(100L);

            messageSender.sendMarkAsRead("reader", "sender", messageIds);

            verify(webSocketService).sendMessage(any(WsMessage.class));
        }

        @Test
        @DisplayName("Debe manejar un solo ID")
        void testSendMarkAsRead_HandlesSingleId() {
            when(webSocketService.isConnected()).thenReturn(true);
            
            List<Long> messageIds = Collections.singletonList(100L);

            messageSender.sendMarkAsRead("reader", "sender", messageIds);

            verify(webSocketService).sendMessage(argThat((WsMessage msg) -> {
                MarkMessagesAsReadRequest req = msg.getMarkMessagesAsReadRequest();
                return req.getMessageIdsList().equals(Collections.singletonList("100"));
            }));
        }

        @Test
        @DisplayName("Debe manejar múltiples IDs")
        void testSendMarkAsRead_HandlesMultipleIds() {
            when(webSocketService.isConnected()).thenReturn(true);
            
            List<Long> messageIds = Arrays.asList(100L, 101L, 102L, 103L, 104L);

            messageSender.sendMarkAsRead("reader", "sender", messageIds);

            verify(webSocketService).sendMessage(argThat((WsMessage msg) -> {
                MarkMessagesAsReadRequest req = msg.getMarkMessagesAsReadRequest();
                return req.getMessageIdsCount() == 5;
            }));
        }

        @Test
        @DisplayName("Debe enviar mensaje con lista vacía de IDs")
        void testSendMarkAsRead_HandlesEmptyList() {
            when(webSocketService.isConnected()).thenReturn(true);
            
            List<Long> messageIds = Collections.emptyList();

            messageSender.sendMarkAsRead("reader", "sender", messageIds);

            verify(webSocketService).sendMessage(argThat((WsMessage msg) -> {
                MarkMessagesAsReadRequest req = msg.getMarkMessagesAsReadRequest();
                return req.getMessageIdsList().isEmpty();
            }));
        }
    }
}
