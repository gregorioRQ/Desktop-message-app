package com.basic_chat.chat_service.handler;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.chat_service.util.WebSocketValidationUtil;
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
    private final WebSocketValidationUtil validationUtil;

    public MarkAsReadHandler(MessageService messageService, SessionManager sessionManager, WebSocketValidationUtil validationUtil) {
        this.messageService = messageService;
        this.sessionManager = sessionManager;
        this.validationUtil = validationUtil;
    }

    @Override
    public boolean supports(WsMessage message) {
        return message.hasMarkMessagesAsReadRequest();
    }

    /**
     * Procesa la solicitud que marca como visto el mensaje.
     * 
     * Flujo:
     * 1. Valida que el contexto sea válido
     * 2. Valida que el mensaje protobuf sea correcto
     * 3. Extrae el usuario que marca los mensajes como leídos
     * 4. Actualiza los mensajes en la BD como leídos
     * 5. Agrupa mensajes por remitente para notificación
     * 6. Notifica a cada remitente en tiempo real o como pendiente
     * 
     * @param context contexto de la sesión websocket del usuario que solicita marcar los mensajes como vistos
     * @param message mensaje protobuf con la solicitud
     */
    @Override
    public void handle(SessionContext context, WsMessage message) throws Exception {
        try {
            // ==================== VALIDACIÓN INICIAL ====================
            if (!validationUtil.isValidContext(context)) {
                log.error("Contexto nulo o sesión inválida al procesar MarkMessagesAsReadRequest");
                return;
            }

            if (message == null || !validationUtil.isValidProtobufField(message.hasMarkMessagesAsReadRequest(), "MarkMessagesAsReadRequest")) {
                log.warn("Mensaje protobuf nulo o sin MarkMessagesAsReadRequest");
                return;
            }

            MarkMessagesAsReadRequest request = message.getMarkMessagesAsReadRequest();

            // Validar que la lista de IDs no está vacía
            if (!validationUtil.isValidList(request.getMessageIdsList(), "messageIdsList")) {
                log.warn("Lista de IDs de mensajes vacía o nula");
                return;
            }

            // ==================== OBTENER USUARIO LECTOR ====================
            String reader;
            try {
                SessionManager.SessionInfo sessionInfo = sessionManager.getSessionInfo(context.getSession().getId());
                
                if (sessionInfo == null) {
                    log.error("SessionInfo no encontrada para sesión ID: {}", context.getSession().getId());
                    return;
                }

                reader = sessionInfo.getUsername();

                if (!validationUtil.isValidString(reader, "reader")) {
                    log.error("Username del lector es nulo o vacío");
                    return;
                }

            } catch (Exception e) {
                log.error("Error al obtener información de sesión del lector", e);
                return;
            }

            log.debug("Usuario {} marcando mensajes como leídos: {}", reader, request.getMessageIdsList());

            // ==================== ACTUALIZAR BD ====================
            List<Message> updatedMessages = null;
            try {
                updatedMessages = messageService.markMessagesAsRead(request.getMessageIdsList(), reader);
            } catch (Exception e) {
                log.error("Error al marcar mensajes como leídos en BD", e);
                return;
            }

            if (updatedMessages == null || updatedMessages.isEmpty()) {
                log.debug("No se actualizaron mensajes para lector: {}", reader);
                return;
            }

            log.info("Se marcaron {} mensajes como leídos para usuario: {}", updatedMessages.size(), reader);

            // ==================== AGRUPAR MENSAJES POR REMITENTE ====================
            Map<String, List<String>> messagesBySender = updatedMessages.stream()
                .collect(Collectors.groupingBy(
                    Message::getFromUserId,
                    Collectors.mapping(m -> String.valueOf(m.getId()), Collectors.toList())
                ));

            // ==================== NOTIFICAR A CADA REMITENTE ====================
            messagesBySender.forEach((sender, ids) -> {
                try {
                    notifySender(sender, ids, reader);
                } catch (Exception e) {
                    log.error("Error notificando al remitente {} sobre lectura de mensajes", sender, e);
                }
            });

        } catch (Exception e) {
            log.error("Error inesperado al procesar MarkMessagesAsReadRequest", e);
        }
    }

    /**
     * Notifica a un remitente sobre la lectura de sus mensajes.
     * 
     * Si el remitente está online, envía la notificación en tiempo real.
     * Si está offline, guarda la notificación como pendiente.
     * 
     * @param sender nombre del usuario remitente
     * @param messageIds lista de IDs de mensajes leídos
     * @param reader nombre del usuario que leyó los mensajes
     */
    private void notifySender(String sender, List<String> messageIds, String reader) {
        // Validar que los parámetros son válidos
        if (!validationUtil.isValidStrings(sender, "sender", reader, "reader")) {
            log.warn("Sender o reader nulo/vacío. No se notificará");
            return;
        }

        if (!validationUtil.isValidList(messageIds, "messageIds")) {
            log.warn("Lista de messageIds nula o vacía para notificación");
            return;
        }

        try {
            // Si el usuario está online, entregar en tiempo real
            if (sessionManager.isUserOnline(sender)) {
                deliverReadNotificationRealtime(sender, messageIds, reader);
            } else {
                // Si está offline, guardar como pendiente
                savePendingReadReceipt(sender, messageIds, reader);
            }

        } catch (Exception e) {
            log.error("Error al procesar notificación de lectura para remitente: {}", sender, e);
            // Fallback: guardar como pendiente
            try {
                savePendingReadReceipt(sender, messageIds, reader);
            } catch (Exception fallbackError) {
                log.error("Error en fallback al guardar notificación pendiente para remitente: {}", sender, fallbackError);
            }
        }
    }

    /**
     * Entrega la notificación de lectura en tiempo real al remitente.
     * 
     * @param sender nombre del usuario remitente
     * @param messageIds lista de IDs de mensajes leídos
     * @param reader nombre del usuario que leyó los mensajes
     */
    private void deliverReadNotificationRealtime(String sender, List<String> messageIds, String reader) {
        try {
            SessionManager.SessionInfo senderSession = sessionManager.findByUsername(sender);

            if (senderSession == null) {
                log.warn("SessionInfo no encontrada para remitente online: {}", sender);
                savePendingReadReceipt(sender, messageIds, reader);
                return;
            }

            WebSocketSession wsSession = senderSession.getWsSession();

            if (!validationUtil.isValidWebSocketSession(wsSession)) {
                log.warn("WebSocketSession nula o cerrada para remitente: {}", sender);
                savePendingReadReceipt(sender, messageIds, reader);
                return;
            }

            // Construir y enviar la notificación
            MessagesReadUpdate update = MessagesReadUpdate.newBuilder()
                    .addAllMessageIds(messageIds)
                    .setReaderUsername(reader)
                    .build();

            WsMessage wsMessage = WsMessage.newBuilder()
                    .setMessagesReadUpdate(update)
                    .build();

            wsSession.sendMessage(new BinaryMessage(wsMessage.toByteArray()));
            log.info("Notificación de lectura entregada en tiempo real a {}", sender);

        } catch (Exception e) {
            log.error("Error enviando notificación de lectura por WebSocket al remitente: {}", sender, e);
            try {
                savePendingReadReceipt(sender, messageIds, reader);
            } catch (Exception fallbackError) {
                log.error("Error en fallback al guardar notificación pendiente para: {}", sender, fallbackError);
            }
        }
    }

    /**
     * Guarda una notificación de lectura como pendiente en la BD.
     * 
     * @param sender nombre del usuario remitente
     * @param messageIds lista de IDs de mensajes leídos
     * @param reader nombre del usuario que leyó los mensajes
     */
    private void savePendingReadReceipt(String sender, List<String> messageIds, String reader) {
        try {
            if (!validationUtil.isValidStrings(sender, "sender", reader, "reader")) {
                log.warn("Sender o reader inválido. No se puede guardar recibo pendiente");
                return;
            }

            if (!validationUtil.isValidList(messageIds, "messageIds")) {
                log.warn("Lista de messageIds vacía. No se puede guardar recibo pendiente");
                return;
            }

            messageService.savePendingReadReceipts(sender, messageIds, reader);
            log.info("Notificación de lectura guardada como pendiente - remitente: {}, lector: {}", sender, reader);

        } catch (Exception e) {
            log.error("Error al guardar notificación de lectura pendiente para remitente: {}, lector: {}", 
                    sender, reader, e);
        }
    }
}
