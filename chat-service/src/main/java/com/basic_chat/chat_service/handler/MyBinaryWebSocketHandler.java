package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.chat_service.service.RedisSessionService;
import com.basic_chat.chat_service.service.WsMessageDispatcher;
import com.basic_chat.chat_service.service.WebSocketConnectionManager;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * Manejador de mensajes WebSocket binarios.
 * 
 * NOTA: Se asume que la validación del token ya fue hecha por el API Gateway.
 */
@Component
@Slf4j
public class MyBinaryWebSocketHandler extends AbstractWebSocketHandler {

    private final SessionManager sessionManager;
    private final WsMessageDispatcher dispatcher;
    private final WebSocketConnectionManager connectionManager;
    private final PendingMessagesHandler pendingMessagesHandler;
    private final RedisSessionService redisSessionService;

    public MyBinaryWebSocketHandler(
            SessionManager sessionManager,
            WsMessageDispatcher dispatcher,
            WebSocketConnectionManager connectionManager,
            PendingMessagesHandler pendingMessagesHandler,
            RedisSessionService redisSessionService) {
        this.sessionManager = sessionManager;
        this.dispatcher = dispatcher;
        this.connectionManager = connectionManager;
        this.pendingMessagesHandler = pendingMessagesHandler;
        this.redisSessionService = redisSessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Extraer userId y username de las cabeceras (inyectadas por el API Gateway)
        String userId = extractHeader(session, "X-User-ID");
        String username = extractHeader(session, "X-Username");

        if (userId == null || username == null || !isSessionValidInRedis(userId)) {
            log.warn("Cerrando sesión {}: cabeceras incompletas o sesión de Redis no válida. UserId: {}, Username: {}", 
                     session.getId(), userId, username);
            session.close(CloseStatus.POLICY_VIOLATION.withReason("User-ID/Username headers missing or invalid session"));
            return;
        }

        // Registrar la sesión completamente en el SessionManager local
        sessionManager.registerSession(session.getId(), userId, username, session);
        
        // Guardar el sessionId en Redis como backup/consistencia
        //redisSessionService.saveSessionId(userId, session.getId());

        log.info("Sesión registrada - sessionId: {}, userId: {}, username: {}", session.getId(), userId, username);

        // Ahora que la sesión está registrada, enviar todos los mensajes/eventos pendientes
        sendAllPendingMessages(session, username);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        try {
            // Validar que la sesión esté completamente registrada antes de procesar mensajes
            if (sessionManager.getSessionInfo(session.getId()) == null) {
                log.warn("Mensaje recibido en sesión no registrada: {}. Ignorando.", session.getId());
                return;
            }

            MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.parseFrom(message.getPayload().array());
            SessionContext context = new SessionContext(session, sessionManager);

            // Despachar el mensaje (todos ya son autenticados por el gateway)
            dispatcher.dispatch(context, wsMessage);
        } catch (Exception e) {
            log.error("Error procesando mensaje WebSocket - ID sesión: {}", session.getId(), e);
            closeSession(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("Mensaje de texto recibido - ID sesión: {}, contenido: {}", 
                session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        connectionManager.handleConnectionClosed(session, status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        connectionManager.handleTransportError(session, exception);
    }

    private void sendAllPendingMessages(WebSocketSession session, String username) {
        try {
            pendingMessagesHandler.sendPendingMessages(session, username);
            pendingMessagesHandler.sendPendingDeletions(session, username);
            pendingMessagesHandler.sendPendingUnblocks(session, username);
            pendingMessagesHandler.sendPendingReadReceipts(session, username);
            pendingMessagesHandler.sendPendingContactIdentities(session, username); 
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    private void closeSession(WebSocketSession session) {
        try {
            session.close();
            log.info("Sesión cerrada por error - ID sesión: {}", session.getId());
        } catch (Exception e) {
            log.error("Error al cerrar sesión - ID sesión: {}", session.getId(), e);
        }
    }

    private String extractHeader(WebSocketSession session, String headerName) {
        try {
            String headerValue = session.getHandshakeHeaders().getFirst(headerName);
            return (headerValue != null && !headerValue.trim().isEmpty()) ? headerValue : null;
        } catch (Exception e) {
            log.error("Error al procesar cabecera {}", headerName, e);
            return null;
        }
    }

    private boolean isSessionValidInRedis(String userId) {
        if (!redisSessionService.hasSessionMapping(userId)) {
            log.warn("Conexión rechazada: No se encontró sesión en Redis para userId={}", userId);
            return false;
        }
        return true;
    }
}
