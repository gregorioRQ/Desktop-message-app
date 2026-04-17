package com.basic_chat.chat_service.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.basic_chat.chat_service.models.PendingReadReceipt;
import com.basic_chat.chat_service.repository.PendingReadReceiptRepository;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.MarkMessagesAsReadRequest;
import com.basic_chat.proto.MessagesProto.WsMessage;

/**
 * Tests unitarios para OfflineMarkAsReadHandler.
 * 
 * Este handler procesa confirmaciones de lectura cuando el emisor original
 * del mensaje está offline. Guarda los recibos pendientes en la base de datos
 * para ser entregados cuando el emisor se reconecte.
 * 
 * Flujo: Cliente (lector) → connection-service → RabbitMQ (offline) → 
 *        chat-service → OfflineMarkAsReadHandler → PendingReadReceipt
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OfflineMarkAsReadHandler Tests")
class OfflineMarkAsReadHandlerTest {

    @Mock
    private PendingReadReceiptRepository pendingReadReceiptRepository;

    private OfflineMarkAsReadHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OfflineMarkAsReadHandler(pendingReadReceiptRepository);
    }

    @Nested
    @DisplayName("Tests para supports()")
    class SupportsTests {

        @Test
        @DisplayName("Debe retornar true cuando el mensaje tiene MarkMessagesAsReadRequest")
        void testSupports_ReturnsTrueForMarkMessagesAsReadRequest() {
            MarkMessagesAsReadRequest readRequest = MarkMessagesAsReadRequest.newBuilder()
                    .setSender("reader")
                    .setRecipient("sender")
                    .addMessageIds("msg1")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMarkMessagesAsReadRequest(readRequest)
                    .build();

            boolean result = handler.supports(message);

            assertTrue(result);
        }

        @Test
        @DisplayName("Debe retornar false cuando el mensaje no tiene MarkMessagesAsReadRequest")
        void testSupports_ReturnsFalseForOtherMessageTypes() {
            WsMessage message = WsMessage.newBuilder()
                    .setChatMessageResponse(MessagesProto.ChatMessageResponse.getDefaultInstance())
                    .build();

            boolean result = handler.supports(message);

            assertFalse(result);
        }

        @Test
        @DisplayName("Debe retornar false para mensaje vacío")
        void testSupports_ReturnsFalseForEmptyMessage() {
            WsMessage message = WsMessage.getDefaultInstance();

            boolean result = handler.supports(message);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Tests para handleOffline()")
    class HandleOfflineTests {

        @Test
        @DisplayName("Debe guardar un pending receipt por cada messageId en la lista")
        void testHandleOffline_SavesMultiplePendingReadReceipts() throws Exception {
            String reader = "userReader";
            String sender = "userSender";
            List<String> messageIds = List.of("msg1", "msg2", "msg3");

            MarkMessagesAsReadRequest readRequest = MarkMessagesAsReadRequest.newBuilder()
                    .setSender(reader)
                    .setRecipient(sender)
                    .addAllMessageIds(messageIds)
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMarkMessagesAsReadRequest(readRequest)
                    .build();

            handler.handleOffline(message, sender);

            verify(pendingReadReceiptRepository, times(3)).save(any(PendingReadReceipt.class));
        }

        @Test
        @DisplayName("Debe guardar pending receipt con los datos correctos")
        void testHandleOffline_SavesCorrectDataInPendingReceipt() throws Exception {
            String reader = "userReader";
            String sender = "userSender";
            String messageId = "msg123";

            MarkMessagesAsReadRequest readRequest = MarkMessagesAsReadRequest.newBuilder()
                    .setSender(reader)
                    .setRecipient(sender)
                    .addMessageIds(messageId)
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMarkMessagesAsReadRequest(readRequest)
                    .build();

            handler.handleOffline(message, sender);

            verify(pendingReadReceiptRepository).save(argEquals((PendingReadReceipt pendingReceipt) -> {
                assertEquals(messageId, pendingReceipt.getMessageId());
                assertEquals(sender, pendingReceipt.getReceiptRecipient());
                assertEquals(reader, pendingReceipt.getReader());
            }));
        }

        @Test
        @DisplayName("Debe manejar lista vacía de messageIds sin errores")
        void testHandleOffline_HandlesEmptyMessageIdsList() throws Exception {
            MarkMessagesAsReadRequest readRequest = MarkMessagesAsReadRequest.newBuilder()
                    .setSender("reader")
                    .setRecipient("sender")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMarkMessagesAsReadRequest(readRequest)
                    .build();

            handler.handleOffline(message, "sender");

            verify(pendingReadReceiptRepository, never()).save(any());
        }

        @Test
        @DisplayName("Debe propagar excepción del repositorio")
        void testHandleOffline_PropagatesRepositoryException() throws Exception {
            MarkMessagesAsReadRequest readRequest = MarkMessagesAsReadRequest.newBuilder()
                    .setSender("reader")
                    .setRecipient("sender")
                    .addMessageIds("msg1")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMarkMessagesAsReadRequest(readRequest)
                    .build();

            doThrow(new RuntimeException("DB Error")).when(pendingReadReceiptRepository).save(any());

            assertThrows(RuntimeException.class, () -> handler.handleOffline(message, "sender"));
        }
    }

    /**
     * Helper para verificar argumentos con assertions personalizadas.
     */
    private static PendingReadReceipt argEquals(java.util.function.Consumer<PendingReadReceipt> assertions) {
        return argThat(pendingReceipt -> {
            assertions.accept(pendingReceipt);
            return true;
        });
    }
}
