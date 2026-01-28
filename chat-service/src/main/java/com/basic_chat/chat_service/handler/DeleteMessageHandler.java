package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.chat_service.util.WebSocketValidationUtil;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.DeleteMessageRequest;
import com.basic_chat.proto.MessagesProto.DeleteMessageResponse;
import com.basic_chat.proto.MessagesProto.WsMessage;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DeleteMessageHandler implements WsMessageHandler {
    
    private final MessageService messageService;
    private final SessionManager sessionManager;
    private final WebSocketValidationUtil validationUtil;

    public DeleteMessageHandler(MessageService messageService, SessionManager sessionManager, WebSocketValidationUtil validationUtil) {
        this.messageService = messageService;
        this.sessionManager = sessionManager;
        this.validationUtil = validationUtil;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasDeleteMessageRequest();
    }

    /**
     * Procesa la eliminación de un mensaje en la aplicación de chat.
     * 
     * Flujo:
     * 1. Valida que el contexto y el mensaje sean válidos
     * 2. Extrae el ID del mensaje a eliminar
     * 3. Ejecuta la eliminación en la base de datos
     * 4. Si el receptor está online: notifica en tiempo real
     * 5. Si el receptor está offline: guarda la eliminación como pendiente
     * 6. Envía respuesta de éxito/error al solicitante
     * 
     * @param context contexto de la sesión WebSocket del usuario que solicita eliminación
     * @param message mensaje protobuf con la solicitud de eliminación
     */
    @Override
    public void handle(SessionContext context, MessagesProto.WsMessage message) {
        String messageId = null;
        String recipient = null;
        boolean success = false;
        String responseMessage = "Error desconocido al eliminar mensaje";

        try {
            // ==================== VALIDACIÓN INICIAL ====================
            // Validar que el contexto es válido
            if (!validationUtil.isValidContext(context)) {
                log.error("Contexto nulo o sesión inválida al procesar DeleteMessageRequest");
                return;
            }

            WebSocketSession senderSession = context.getSession();

            // Validar que el mensaje protobuf es válido
            if (message == null || !validationUtil.isValidProtobufField(message.hasDeleteMessageRequest(), "DeleteMessageRequest")) {
                log.warn("Mensaje protobuf nulo o sin DeleteMessageRequest");
                sendDeleteResponse(senderSession, false, "Solicitud inválida");
                return;
            }

            DeleteMessageRequest request = message.getDeleteMessageRequest();
            messageId = request.getMessageId();

            // Validar que el ID del mensaje está presente
            if (!validationUtil.isValidString(messageId, "messageId")) {
                log.warn("Message ID nulo o vacío en DeleteMessageRequest");
                sendDeleteResponse(senderSession, false, "ID de mensaje inválido");
                return;
            }

            log.info("Procesando solicitud de eliminación para mensaje ID: {}", messageId);

            // ==================== ELIMINAR MENSAJE ====================
            Message deletedMessage = deleteMessageFromDatabase(request);

            if (deletedMessage == null) {
                log.warn("Mensaje no encontrado o no se pudo eliminar: {}", messageId);
                sendDeleteResponse(senderSession, false, "Mensaje no encontrado");
                return;
            }

            recipient = deletedMessage.getToUserId();
            success = true;
            responseMessage = "Mensaje eliminado correctamente";
            log.info("Mensaje {} eliminado exitosamente de la BD", messageId);

            // ==================== NOTIFICAR AL RECEPTOR ====================
            notifyRecipientOfDeletion(recipient, message);

        } catch (Exception e) {
            log.error("Error inesperado al procesar eliminación de mensaje ID: {}", messageId, e);
            responseMessage = "Error al eliminar mensaje: " + e.getMessage();
        }

        // ==================== ENVIAR RESPUESTA AL SOLICITANTE ====================
        try {
            sendDeleteResponse(context.getSession(), success, responseMessage);
        } catch (Exception e) {
            log.error("Error enviando respuesta de eliminación al cliente", e);
        }
    }

    /**
     * Elimina el mensaje de la base de datos.
     * 
     * Encapsula la lógica de eliminación en BD con manejo de excepciones.
     * 
     * @param messageId ID del mensaje a eliminar
     * @return el mensaje eliminado, o null si no se encontró
     */
    private Message deleteMessageFromDatabase(DeleteMessageRequest request) {
        try {
            Message deletedMessage = messageService.deleteMessage(request);
            
            if (deletedMessage == null) {
                log.warn("MessageService retornó null para mensaje ID: {}", request.getMessageId());
                return null;
            }

            return deletedMessage;

        } catch (IllegalArgumentException e) {
            log.warn("Validación fallida al eliminar mensaje ID: {}: {}", request.getMessageId(), e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("Error inesperado en base de datos al eliminar mensaje ID: {}", request.getMessageId(), e);
            throw new RuntimeException("Error al eliminar mensaje de BD: " + e.getMessage(), e);
        }
    }

    /**
     * Notifica al receptor sobre la eliminación del mensaje.
     * 
     * Si el receptor está online, envía notificación en tiempo real.
     * Si está offline, guarda la eliminación como pendiente.
     * 
     * @param recipient nombre de usuario del receptor
     * @param message mensaje protobuf a reenvia
     */
    private void notifyRecipientOfDeletion(String recipient, WsMessage message) {
        if (!validationUtil.isValidString(recipient, "recipient")) {
            log.warn("Recipient nulo o vacío. No se notificará la eliminación");
            return;
        }

        try {
            if (!sessionManager.isUserOnline(recipient)) {
                log.debug("Usuario {} offline. Guardando eliminación como pendiente", recipient);
                savePendingDeletion(recipient, message.getDeleteMessageRequest().getMessageId());
                return;
            }

            deliverDeletionNotificationRealtime(recipient, message);

        } catch (Exception e) {
            log.error("Error al procesar notificación de eliminación para receptor: {}", recipient, e);
            try {
                savePendingDeletion(recipient, message.getDeleteMessageRequest().getMessageId());
            } catch (Exception fallbackError) {
                log.error("Error en fallback al guardar eliminación pendiente para receptor: {}", recipient, fallbackError);
            }
        }
    }

    /**
     * Entrega la notificación de eliminación en tiempo real al receptor.
     * 
     * Busca la sesión WebSocket del receptor y envía el mensaje.
     * Si la sesión no existe o está cerrada, guarda como pendiente.
     * 
     * @param recipient nombre de usuario del receptor
     * @param message mensaje protobuf a enviar
     */
    private void deliverDeletionNotificationRealtime(String recipient, WsMessage message) {
        try {
            SessionManager.SessionInfo recipientInfo = sessionManager.findByUsername(recipient);

            if (recipientInfo == null) {
                log.warn("SessionInfo no encontrada para usuario online: {}", recipient);
                savePendingDeletion(recipient, message.getDeleteMessageRequest().getMessageId());
                return;
            }

            WebSocketSession recipientSession = recipientInfo.getWsSession();

            if (!validationUtil.isValidWebSocketSession(recipientSession)) {
                log.warn("WebSocketSession nula o cerrada para usuario: {}. Guardando como pendiente", recipient);
                savePendingDeletion(recipient, message.getDeleteMessageRequest().getMessageId());
                return;
            }

            recipientSession.sendMessage(new BinaryMessage(message.toByteArray()));
            log.info("Notificación de eliminación entregada en tiempo real a {}", recipient);

        } catch (Exception e) {
            log.error("Error enviando notificación de eliminación por WebSocket al usuario: {}", recipient, e);
            try {
                savePendingDeletion(recipient, message.getDeleteMessageRequest().getMessageId());
            } catch (Exception fallbackError) {
                log.error("Error en fallback al guardar eliminación pendiente para: {}", recipient, fallbackError);
            }
        }
    }

    /**
     * Guarda una solicitud de eliminación como pendiente en la base de datos.
     * 
     * Se usa cuando el receptor está offline o la entrega en tiempo real falla.
     * 
     * @param recipient nombre de usuario del receptor
     * @param messageId ID del mensaje que fue eliminado
     */
    private void savePendingDeletion(String recipient, String messageId) {
        try {
            if (!validationUtil.isValidStrings(recipient, "recipient", messageId, "messageId")) {
                log.warn("Datos inválidos. No se puede guardar eliminación pendiente");
                return;
            }

            messageService.savePendingDeletion(recipient, messageId);
            log.info("Eliminación pendiente guardada - receptor: {}, mensaje: {}", recipient, messageId);

        } catch (Exception e) {
            log.error("Error al guardar eliminación pendiente en BD para receptor: {}, mensaje: {}", 
                    recipient, messageId, e);
        }
    }

    /**
     * Envía una respuesta de éxito o error al cliente solicitante.
     * 
     * @param session sesión WebSocket del usuario que solicitó la eliminación
     * @param success indica si la eliminación fue exitosa
     * @param message mensaje descriptivo de la respuesta
     */
    private void sendDeleteResponse(WebSocketSession session, boolean success, String message) {
        try {
            if (!validationUtil.isValidWebSocketSession(session)) {
                log.warn("No se puede enviar respuesta: sesión nula o cerrada");
                return;
            }

            DeleteMessageResponse response = DeleteMessageResponse.newBuilder()
                    .setSuccess(success)
                    .setMessage(message)
                    .build();

            WsMessage wsResponse = WsMessage.newBuilder()
                    .setDeleteMessageResponse(response)
                    .build();

            session.sendMessage(new BinaryMessage(wsResponse.toByteArray()));
            log.info("Respuesta de eliminación enviada al cliente - Éxito: {}", success);

        } catch (Exception e) {
            log.error("Error enviando respuesta de eliminación al cliente", e);
        }
    }
}
