package com.basic_chat.connection_service.handler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.basic_chat.connection_service.service.MessageRouterService;
import com.basic_chat.proto.MessagesProto.MarkMessagesAsReadRequest;
import com.basic_chat.proto.MessagesProto.WsMessage;
import com.basic_chat.proto.MessagesProto.ChatMessageResponse;
import com.basic_chat.proto.MessagesProto.ChatMessage;
import com.basic_chat.proto.MessagesProto.DeleteMessageRequest;

/**
 * Tests unitarios para MarkAsReadHandler.
 * 
 * Este handler procesa las solicitudes de confirmación de lectura (MarkMessagesAsReadRequest)
 * recibidas desde el cliente WebSocket. Delega el enrutamiento del mensaje al MessageRouterService.
 * 
 * Flujo cuando ambos usuarios están online:
 * Cliente (uB) → WebSocket → connection-service → MarkAsReadHandler → 
 * MessageRouterService → WebSocket → Cliente (uA)
 * 
 * Flujo cuando el emisor está offline:
 * Cliente (uB) → WebSocket → connection-service → MarkAsReadHandler → 
 * MessageRouterService → RabbitMQ (offline) → chat-service
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MarkAsReadHandler Tests")
class MarkAsReadHandlerTest {

    @Mock
    private MessageRouterService messageRouterService;

    private MarkAsReadHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MarkAsReadHandler(messageRouterService);
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

            assertTrue(result, "supports() debe retornar true para MarkMessagesAsReadRequest");
        }

        @Test
        @DisplayName("Debe retornar false cuando el mensaje no tiene MarkMessagesAsReadRequest")
        void testSupports_ReturnsFalseForOtherMessageTypes() {
            WsMessage message = WsMessage.newBuilder()
                    .setChatMessageResponse(ChatMessageResponse.getDefaultInstance())
                    .build();

            boolean result = handler.supports(message);

            assertFalse(result, "supports() debe retornar false para otros tipos de mensaje");
        }

        @Test
        @DisplayName("Debe retornar false para mensaje vacío")
        void testSupports_ReturnsFalseForEmptyMessage() {
            WsMessage message = WsMessage.getDefaultInstance();

            boolean result = handler.supports(message);

            assertFalse(result, "supports() debe retornar false para mensaje vacío");
        }

        @Test
        @DisplayName("Debe retornar false para mensaje con ChatMessage")
        void testSupports_ReturnsFalseForChatMessage() {
            WsMessage message = WsMessage.newBuilder()
                    .setChatMessage(ChatMessage.getDefaultInstance())
                    .build();

            boolean result = handler.supports(message);

            assertFalse(result);
        }

        @Test
        @DisplayName("Debe retornar false para mensaje con DeleteMessageRequest")
        void testSupports_ReturnsFalseForDeleteMessageRequest() {
            WsMessage message = WsMessage.newBuilder()
                    .setDeleteMessageRequest(DeleteMessageRequest.getDefaultInstance())
                    .build();

            boolean result = handler.supports(message);

            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("Tests para handle()")
    class HandleTests {

        @Test
        @DisplayName("Debe llamar routeMessage con sender y recipient correctos")
        void testHandle_CallsRouteMessageWithCorrectParameters() {
            String sender = "readerUser";
            String recipient = "senderUser";
            String messageId = "msg123";

            MarkMessagesAsReadRequest readRequest = MarkMessagesAsReadRequest.newBuilder()
                    .setSender(sender)
                    .setRecipient(recipient)
                    .addMessageIds(messageId)
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMarkMessagesAsReadRequest(readRequest)
                    .build();

            handler.handle(sender, message);

            verify(messageRouterService).routeMessage(eq(sender), eq(recipient), any(byte[].class));
        }

        @Test
        @DisplayName("Debe serializar el mensaje correctamente")
        void testHandle_SerializesMessageCorrectly() {
            String sender = "readerUser";
            String recipient = "senderUser";

            MarkMessagesAsReadRequest readRequest = MarkMessagesAsReadRequest.newBuilder()
                    .setSender(sender)
                    .setRecipient(recipient)
                    .addMessageIds("msg1")
                    .addMessageIds("msg2")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMarkMessagesAsReadRequest(readRequest)
                    .build();

            byte[] expectedData = message.toByteArray();

            handler.handle(sender, message);

            verify(messageRouterService).routeMessage(eq(sender), eq(recipient), eq(expectedData));
        }

        @Test
        @DisplayName("Debe manejar múltiples messageIds")
        void testHandle_HandlesMultipleMessageIds() {
            String sender = "readerUser";
            String recipient = "senderUser";

            MarkMessagesAsReadRequest readRequest = MarkMessagesAsReadRequest.newBuilder()
                    .setSender(sender)
                    .setRecipient(recipient)
                    .addMessageIds("msg1")
                    .addMessageIds("msg2")
                    .addMessageIds("msg3")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMarkMessagesAsReadRequest(readRequest)
                    .build();

            handler.handle(sender, message);

            verify(messageRouterService).routeMessage(eq(sender), eq(recipient), any(byte[].class));
        }

        @Test
        @DisplayName("No debe lanzar excepción cuando routeMessage falla")
        void testHandle_DoesNotThrowOnRouteMessageException() {
            doThrow(new RuntimeException("Routing failed"))
                    .when(messageRouterService).routeMessage(any(), any(), any());

            MarkMessagesAsReadRequest readRequest = MarkMessagesAsReadRequest.newBuilder()
                    .setSender("reader")
                    .setRecipient("sender")
                    .addMessageIds("msg1")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMarkMessagesAsReadRequest(readRequest)
                    .build();

            assertDoesNotThrow(() -> handler.handle("reader", message),
                    "handle() no debe propagar excepciones, solo loguearlas");
        }

        @Test
        @DisplayName("Debe loguear error cuando routeMessage falla")
        void testHandle_LogsErrorOnException() {
            RuntimeException expectedException = new RuntimeException("Routing failed");
            doThrow(expectedException)
                    .when(messageRouterService).routeMessage(any(), any(), any());

            MarkMessagesAsReadRequest readRequest = MarkMessagesAsReadRequest.newBuilder()
                    .setSender("reader")
                    .setRecipient("sender")
                    .addMessageIds("msg1")
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMarkMessagesAsReadRequest(readRequest)
                    .build();

            handler.handle("reader", message);

            verify(messageRouterService).routeMessage(any(), any(), any());
        }

        @Test
        @DisplayName("Debe procesar mensaje con lista vacía de messageIds")
        void testHandle_HandlesEmptyMessageIdsList() {
            String sender = "readerUser";
            String recipient = "senderUser";

            MarkMessagesAsReadRequest readRequest = MarkMessagesAsReadRequest.newBuilder()
                    .setSender(sender)
                    .setRecipient(recipient)
                    .build();

            WsMessage message = WsMessage.newBuilder()
                    .setMarkMessagesAsReadRequest(readRequest)
                    .build();

            handler.handle(sender, message);

            verify(messageRouterService).routeMessage(eq(sender), eq(recipient), any(byte[].class));
        }
    }
}
