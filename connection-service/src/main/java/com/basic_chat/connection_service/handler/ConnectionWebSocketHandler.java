package com.basic_chat.connection_service.handler;

import com.basic_chat.connection_service.service.ConnectionMessageDispatcher;
import com.basic_chat.connection_service.service.PendingMessagesService;
import com.basic_chat.connection_service.service.SessionRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ConnectionWebSocketHandler extends AbstractWebSocketHandler {

    private final SessionRegistryService sessionRegistryService;
    private final ConnectionMessageDispatcher messageDispatcher;
    private final PendingMessagesService pendingMessagesService;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public ConnectionWebSocketHandler(
            SessionRegistryService sessionRegistryService,
            ConnectionMessageDispatcher messageDispatcher,
            PendingMessagesService pendingMessagesService) {
        this.sessionRegistryService = sessionRegistryService;
        this.messageDispatcher = messageDispatcher;
        this.pendingMessagesService = pendingMessagesService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = getHeader(session, "X-User-ID");
        String username = getHeader(session, "X-Username");

        if (userId == null || username == null) {
            log.warn("Cerrando conexión: cabeceras faltantes. UserId: {}, Username: {}", userId, username);
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing headers"));
            return;
        }

        sessionRegistryService.registerSession(session.getId(), userId, username, session);
        sessions.put(session.getId(), session);
        log.info("Cliente conectado - sessionId: {}, username: {}", session.getId(), username);

        sendPendingMessages(session, username);
    }

    private void sendPendingMessages(WebSocketSession session, String username) {
        try {
            List<byte[]> pendingMessages = pendingMessagesService.getPendingMessages(username);

            if (pendingMessages.isEmpty()) {
                log.info("No hay mensajes pendientes para {}", username);
                return;
            }

            log.info("Enviando {} mensajes pendientes a {}", pendingMessages.size(), username);

            for (byte[] messageData : pendingMessages) {
                if (session.isOpen()) {
                    session.sendMessage(new BinaryMessage(messageData));
                }
            }

            log.info("Mensajes pendientes enviados exitosamente a {}", username);

        } catch (Exception e) {
            log.error("Error enviando mensajes pendientes a {}: {}", username, e.getMessage());
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        try {
            log.info("Procesando mensaje binario");
            String sessionId = session.getId();
            SessionRegistryService.SessionInfo sessionInfo = sessionRegistryService.getSession(sessionId);

            if (sessionInfo == null) {
                log.warn("Mensaje de sesión no registrada: {}", sessionId);
                return;
            }

            byte[] data = message.getPayload().array();
            String sender = sessionInfo.getUsername();

            com.basic_chat.proto.MessagesProto.WsMessage wsMessage =
                    com.basic_chat.proto.MessagesProto.WsMessage.parseFrom(data);

            // Usar el dispatcher para enrutar el mensaje al handler apropiado
            messageDispatcher.dispatch(sender, wsMessage);

        } catch (Exception e) {
            log.error("Error procesando mensaje de {}: {}", session.getId(), e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.info("Mensaje de texto recibido - sessionId: {}, contenido: {}", session.getId(), message.getPayload());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionRegistryService.removeSession(session.getId());
        sessions.remove(session.getId());
        log.info("Cliente desconectado - sessionId: {}, código: {}", session.getId(), status.getCode());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("Error en WebSocket - sessionId: {}, error: {}", session.getId(), exception.getMessage());
        sessionRegistryService.removeSession(session.getId());
        sessions.remove(session.getId());
    }

    private String getHeader(WebSocketSession session, String headerName) {
        try {
            String value = session.getHandshakeHeaders().getFirst(headerName);
            return (value != null && !value.trim().isEmpty()) ? value : null;
        } catch (Exception e) {
            log.error("Error al procesar cabecera {}", headerName, e);
            return null;
        }
    }
}
