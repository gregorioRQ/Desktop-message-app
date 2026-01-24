package com.basic_chat.chat_service.handler;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.MessageSentEvent;
import com.basic_chat.chat_service.service.BlockService;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ChatMessageHandler implements WsMessageHandler{

    private final SessionManager sessionManager;
    private final MessageService messageService;
    private final RabbitTemplate rabbitTemplate;
    private final BlockService blockService;

    public ChatMessageHandler(SessionManager sessionManager, MessageService messageService, RabbitTemplate rabbitTemplate, BlockService blockService) {
        this.sessionManager = sessionManager;
        this.messageService = messageService;
        this.rabbitTemplate = rabbitTemplate;
        this.blockService = blockService;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasChatMessage();
    }

    /**
     * Procesa un mensaje de chat recibido a través de WebSocket.
     * 
     * Flujo:
     * 1. Extrae los datos del mensaje (remitente, destinatario, contenido)
     * 2. Valida que el destinatario no haya bloqueado al remitente
     * 3. Si está bloqueado: envía respuesta de error y retorna
     * 4. Si el destinatario está online: entrega el mensaje en tiempo real
     * 5. Si el destinatario está offline: publica un evento para guardarlo en cola
     * 6. Guarda el mensaje en la base de datos (historial)
     * 
     * @param context contexto de la sesión WebSocket del remitente
     * @param message mensaje protobuf que contiene los datos del chat
     * @throws Exception si ocurre un error durante el procesamiento
     */
    @Override
    public void handle(SessionContext context, MessagesProto.WsMessage message) throws Exception {
        MessagesProto.ChatMessage chat = message.getChatMessage();
        String recipient = chat.getRecipient();

        // Verificar si el destinatario ha bloqueado al remitente
        if (blockService.isBlocked(chat.getSender(), recipient)) {
            log.info("Mensaje bloqueado de {} para {}", chat.getSender(), recipient);
            sendBlockErrorResponse(context.getSession(), chat.getId(), recipient);
            return;
        }

        // Obtener la sesión del destinatario si está conectado
        SessionManager.SessionInfo recipientSession = sessionManager.findByUsername(recipient);

        // Si el destinatario está online, entregar el mensaje en tiempo real
        if (sessionManager.isUserOnline(recipient) && recipientSession != null) {
            recipientSession.getWsSession()
                    .sendMessage(new BinaryMessage(message.toByteArray()));
            log.debug("Mensaje entregado en tiempo real a {} desde {}", recipient, chat.getSender());
        } else {
            // Si está offline, publicar evento para cola de mensajes pendientes
            rabbitTemplate.convertAndSend("message.sent", new MessageSentEvent(chat.getSender(), chat.getRecipient()));
            log.debug("Usuario {} offline. Mensaje en cola para entrega posterior", recipient);
        }

        // Guardar el mensaje en la base de datos (historial de chat)
        messageService.saveMessage(chat);
    }

    /**
     * Envía una respuesta de error al remitente indicando que el mensaje fue bloqueado.
     * 
     * @param session sesión WebSocket del remitente
     * @param messageId ID del mensaje que fue bloqueado
     * @param recipient nombre del usuario que bloqueó al remitente
     */
    private void sendBlockErrorResponse(WebSocketSession session, String messageId, String recipient) {
        try {
            MessagesProto.ChatMessageResponse response = MessagesProto.ChatMessageResponse.newBuilder()
                    .setMessageId(messageId)
                    .setSuccess(false)
                    .setCause(MessagesProto.FailureCause.BLOCKED)
                    .setErrorMessage("No puedes enviar mensajes a " + recipient + ".")
                    .setRecipient(recipient)
                    .build();

            MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.newBuilder()
                    .setChatMessageResponse(response)
                    .build();

            session.sendMessage(new BinaryMessage(wsMessage.toByteArray()));
            log.info("Respuesta de bloqueo enviada a {} para mensaje {}", session.getId(), messageId);
        } catch (Exception e) {
            log.error("Error enviando respuesta de bloqueo para mensaje {} a sesión {}", messageId, session.getId(), e);
        }
    }
}
