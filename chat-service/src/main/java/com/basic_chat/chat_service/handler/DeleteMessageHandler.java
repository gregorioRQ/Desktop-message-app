package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.DeleteMessageRequest;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class DeleteMessageHandler implements WsMessageHandler{
    private final MessageService messageService;
    private final SessionManager sessionManager;

    public DeleteMessageHandler(MessageService messageService, SessionManager sessionManager) {
        this.messageService = messageService;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasDeleteMessageRequest();
    }

    /**
     * Procesa la eliminacion de un mensaje.
     * 
     * @param context contexto de la sesion websocket del remitente.
     * @param message el mensaje protobuf con la solicitud de eliminacion.
     * @throws Exception si ocurre un error durante el procesamiento.
     */
    @Override
    public void handle(SessionContext context, MessagesProto.WsMessage message) throws Exception {
        try {
            // Validar que el mensaje y la solicitud no sean nulos
            if (message == null || !message.hasDeleteMessageRequest()) {
                log.error("Mensaje de eliminación inválido recibido");
                throw new IllegalArgumentException("Mensaje de eliminación inválido");
            }
            
            DeleteMessageRequest request = message.getDeleteMessageRequest();
            log.debug("Procesando solicitud de eliminación para mensaje ID: {}", request.getMessageId());
            
            // El servicio se encarga de la validación y eliminación
            Message deletedMessage = messageService.deleteMessage(request);
            log.info("Mensaje eliminado correctamente: {}", request.getMessageId());

            // Notificar al receptor si está en línea
            String recipient = deletedMessage.getToUserId();
            if (recipient == null || recipient.isEmpty()) {
                log.warn("No se pudo obtener el destinatario para notificación de eliminación del mensaje ID: {}", request.getMessageId());
                return;
            }
            
            SessionManager.SessionInfo recipientSession = sessionManager.findByUsername(recipient);

            if (sessionManager.isUserOnline(recipient) && recipientSession != null) {
                try {
                    recipientSession.getWsSession()
                            .sendMessage(new BinaryMessage(message.toByteArray()));
                    log.debug("Notificación de eliminación enviada a {}", recipient);
                } catch (Exception wsEx) {
                    log.warn("Error al enviar notificación de eliminación a {}: {}", recipient, wsEx.getMessage());
                    // Guardar como pendiente si falla el envío
                    messageService.savePendingDeletion(recipient, request.getMessageId());
                    log.info("Eliminación pendiente guardada debido a error en WebSocket para mensaje ID: {}", request.getMessageId());
                }
            } else {
                // Si el usuario está offline, guardamos la solicitud pendiente
                messageService.savePendingDeletion(recipient, request.getMessageId());
                log.info("Usuario {} offline. Eliminación pendiente guardada para mensaje ID: {}", recipient, request.getMessageId());
            }
            
        } catch (IllegalArgumentException ex) {
            log.error("Error de validación en eliminación de mensaje: {}", ex.getMessage(), ex);
            throw ex;
            
        } catch (Exception ex) {
            log.error("Error inesperado al eliminar mensaje: {}", ex.getMessage(), ex);
            throw new Exception("Error al procesar eliminación de mensaje: " + ex.getMessage(), ex);
        }
    }
}
