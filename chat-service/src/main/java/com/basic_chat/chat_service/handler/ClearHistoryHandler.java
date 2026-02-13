package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.chat_service.service.RedisSessionService;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.ClearHistoryRequest;
import com.basic_chat.proto.MessagesProto.ClearHistoryResponse;
import com.basic_chat.proto.MessagesProto.WsMessage;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ClearHistoryHandler implements WsMessageHandler {
    
    private final MessageService messageService;
    private final SessionManager sessionManager;
    private final RedisSessionService redisSessionService;

    public ClearHistoryHandler(MessageService messageService, SessionManager sessionManager, RedisSessionService redisSessionService) {
        this.messageService = messageService;
        this.sessionManager = sessionManager;
        this.redisSessionService = redisSessionService;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasClearHistoryRequest();
    }

    /**
     * Procesa una solicitud de eliminación de historial de chat.
     * 
     * Flujo:
     * 1. Extrae el nombre del usuario que solicita la limpieza desde la sesión autenticada
     * 2. Obtiene el nombre del usuario correspondiente desde la solicitud
     * 3. Valida los datos antes de procesar
     * 4. Elimina todos los mensajes en la base de datos (ambas direcciones)
     * 5. Si el otro usuario está online: reenvia la solicitud para sincronizar su cliente local
     * 6. Envía respuesta de éxito/error al solicitante
     * 
     * @param context contexto de la sesión WebSocket del usuario que solicita
     * @param message mensaje protobuf que contiene la solicitud de eliminación
     * @throws Exception si ocurre un error crítico durante el procesamiento
     */
    @Override
    public void handle(SessionContext context, MessagesProto.WsMessage message) throws Exception {
        WebSocketSession senderSession = context.getSession();
        ClearHistoryRequest request = message.getClearHistoryRequest();
        
        String sender = null;
        String recipient = null;
        boolean success = false;
        String responseMessage = "Error desconocido al eliminar historial";

        try {
            // Obtener información de la sesión del usuario que solicita la eliminación
            SessionManager.SessionInfo senderInfo = sessionManager.getSessionInfo(senderSession.getId());
            
            if (senderInfo == null) {
                log.error("No se encontró información de sesión para ID: {}", senderSession.getId());
                responseMessage = "Error: sesión no encontrada";
                sendResponse(senderSession, false, responseMessage);
                return;
            }

            sender = senderInfo.getUsername();
            recipient = request.getRecipient();

            // Validar que los datos requeridos están presentes y son válidos
            if (!isValidRequest(sender, recipient)) {
                log.warn("Solicitud de clear_history inválida - sender: {}, recipient: {}", sender, recipient);
                responseMessage = "Datos inválidos en la solicitud";
                sendResponse(senderSession, false, responseMessage);
                return;
            }

            log.info("Procesando solicitud de eliminación de historial entre {} y {}", sender, recipient);

            // Eliminar mensajes de la base de datos (ambas direcciones)
            try {
                messageService.deleteAllMessagesBetweenUsers(sender, recipient);
                log.info("Historial eliminado exitosamente entre {} y {}", sender, recipient);
            } catch (Exception e) {
                log.error("Error al eliminar mensajes de la BD entre {} y {}", sender, recipient, e);
                responseMessage = "Error al eliminar historial de la base de datos";
                sendResponse(senderSession, false, responseMessage);
                return;
            }

            // Reenvia la solicitud al destinatario si está en línea para sincronizar su cliente
            forwardClearHistoryToRecipient(recipient, message);

            success = true;
            responseMessage = "Historial eliminado exitosamente";
            log.info("Solicitud de clear_history completada exitosamente para {}", sender);

        } catch (Exception e) {
            log.error("Error inesperado procesando solicitud de clear_history - sender: {}, recipient: {}", 
                    sender, recipient, e);
            responseMessage = "Error inesperado: " + e.getMessage();
        }

        // Enviar respuesta al cliente solicitante
        sendResponse(senderSession, success, responseMessage);
    }

    /**
     * Reenvía la solicitud de eliminación de historial al usuario destinatario si está online.
     * 
     * Flujo:
     * 1. Busca la sesión WebSocket del destinatario
     * 2. Valida que exista sesión y que esté abierta
     * 3. Envía el mensaje para sincronizar su cliente local
     * 4. Registra el resultado en logs
     * 
     * @param recipient nombre del usuario destinatario
     * @param message mensaje protobuf a reenvia
     */
    private void forwardClearHistoryToRecipient(String recipient, WsMessage message) {
        try {
            // Verificar que el usuario está online
            if (!redisSessionService.isUserOnlineByUsername(recipient)) {
                log.debug("Usuario {} está offline. No se reenvia solicitud de clear_history", recipient);
                return;
            }

            // Obtener la sesión del destinatario
            String sessionId = redisSessionService.getSessionIdByUsername(recipient);
            if (sessionId == null) {
                log.warn("No se encontró sessionId para usuario {} aunque está marcado como online", recipient);
                return;
            }
            SessionManager.SessionInfo recipientInfo = sessionManager.getSessionInfo(sessionId);

            if (recipientInfo == null) {
                log.warn("No se encontró sesión para usuario {} aunque está marcado como online", recipient);
                return;
            }

            WebSocketSession recipientSession = recipientInfo.getWsSession();

            // Validar que la sesión está abierta antes de enviar
            if (recipientSession == null || !recipientSession.isOpen()) {
                log.warn("Sesión WebSocket del usuario {} no está abierta o es nula", recipient);
                return;
            }

            // Enviar el mensaje
            recipientSession.sendMessage(new BinaryMessage(message.toByteArray()));
            log.debug("Solicitud de clear_history reenviada exitosamente a {}", recipient);

        } catch (Exception e) {
            log.error("Error al reenviar solicitud de clear_history al usuario {}", recipient, e);
            // No lanzamos excepción aquí porque el historial ya fue eliminado en la BD
            // El reenvío es un feature de sincronización en tiempo real, no crítico
        }
    }

    /**
     * Valida que la solicitud contenga datos válidos.
     * 
     * @param sender nombre del usuario que solicita
     * @param recipient nombre del usuario destinatario
     * @return true si los datos son válidos, false en caso contrario
     */
    private boolean isValidRequest(String sender, String recipient) {
        // Validar que ambos usernames existan y no estén vacíos
        if (sender == null || sender.trim().isEmpty()) {
            log.warn("Username del sender es nulo o vacío");
            return false;
        }

        if (recipient == null || recipient.trim().isEmpty()) {
            log.warn("Username del recipient es nulo o vacío");
            return false;
        }

        // Validar que el usuario no intente limpiar historial consigo mismo
        if (sender.equalsIgnoreCase(recipient)) {
            log.warn("Usuario {} intenta limpiar historial consigo mismo", sender);
            return false;
        }

        return true;
    }

    /**
     * Envía una respuesta de éxito o error al cliente solicitante.
     * 
     * Construye un mensaje protobuf ClearHistoryResponse con el resultado
     * de la operación y lo envía a través de la sesión WebSocket.
     * 
     * @param session sesión WebSocket del usuario que solicitó
     * @param success indica si la operación fue exitosa
     * @param message descripción de la respuesta
     */
    private void sendResponse(WebSocketSession session, boolean success, String message) {
        try {
            // Validar que la sesión esté abierta
            if (session == null || !session.isOpen()) {
                log.warn("No se puede enviar respuesta: sesión nula o cerrada");
                return;
            }

            // Construir y enviar la respuesta
            ClearHistoryResponse response = ClearHistoryResponse.newBuilder()
                    .setSuccess(success)
                    .setMessage(message)
                    .build();

            WsMessage wsResponse = WsMessage.newBuilder()
                    .setClearHistoryResponse(response)
                    .build();

            session.sendMessage(new BinaryMessage(wsResponse.toByteArray()));
            log.info("Respuesta de clear_history enviada - Éxito: {}, Mensaje: {}", success, message);

        } catch (Exception e) {
            log.error("Error enviando respuesta de clear_history al cliente", e);
        }
    }
}
