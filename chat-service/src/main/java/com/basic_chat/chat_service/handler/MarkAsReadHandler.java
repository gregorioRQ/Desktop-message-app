package com.basic_chat.chat_service.handler;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.MarkMessagesAsReadRequest;
import com.basic_chat.proto.MessagesProto.MessagesReadUpdate;
import com.basic_chat.proto.MessagesProto.WsMessage;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class MarkAsReadHandler implements WsMessageHandler {

    private final MessageService messageService;
    private final SessionManager sessionManager;

    public MarkAsReadHandler(MessageService messageService, SessionManager sessionManager) {
        this.messageService = messageService;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean supports(WsMessage message) {
        return message.hasMarkMessagesAsReadRequest();
    }

    @Override
    public void handle(SessionContext context, WsMessage message) throws Exception {
        MarkMessagesAsReadRequest request = message.getMarkMessagesAsReadRequest();
        String reader = sessionManager.getSessionInfo(context.getSession().getId()).getUsername();
        
        log.debug("Usuario {} marcando mensajes como leídos: {}", reader, request.getMessageIdsList());

        // 1. Actualizar DB
        List<Message> updatedMessages = messageService.markMessagesAsRead(request.getMessageIdsList(), reader);

        if (updatedMessages.isEmpty()) {
            return;
        }

        // 2. Agrupar mensajes por remitente (quien debe recibir la notificación)
        Map<String, List<String>> messagesBySender = updatedMessages.stream()
            .collect(Collectors.groupingBy(
                Message::getFromUserId,
                Collectors.mapping(m -> String.valueOf(m.getId()), Collectors.toList())
            ));

        // 3. Notificar a cada remitente
        messagesBySender.forEach((sender, ids) -> {
            notifySender(sender, ids, reader);
        });
    }

    private void notifySender(String sender, List<String> messageIds, String reader) {
        SessionManager.SessionInfo senderSession = sessionManager.findByUsername(sender);

        if (sessionManager.isUserOnline(sender) && senderSession != null) {
            try {
                MessagesReadUpdate update = MessagesReadUpdate.newBuilder()
                        .addAllMessageIds(messageIds)
                        .setReaderUsername(reader)
                        .build();

                WsMessage wsMessage = WsMessage.newBuilder()
                        .setMessagesReadUpdate(update)
                        .build();

                senderSession.getWsSession().sendMessage(new BinaryMessage(wsMessage.toByteArray()));
                log.debug("Notificación de lectura enviada a {}", sender);
            } catch (Exception e) {
                log.error("Error enviando notificación de lectura a {}", sender, e);
            }
        } else {
            // Guardar pendiente si está offline
            messageService.savePendingReadReceipts(sender, messageIds, reader);
            log.debug("Usuario {} offline. Notificación de lectura guardada.", sender);
        }
    }
}
