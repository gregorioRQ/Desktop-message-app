package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.security.JwtValidator;
import com.basic_chat.chat_service.service.AuthenticationGuard;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.chat_service.service.WsMessageDispatcher;
import com.basic_chat.chat_service.service.WebSocketConnectionManager;
import com.basic_chat.chat_service.service.PendingMessagesHandler;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@Component
@Slf4j
public class MyBinaryWebSocketHandler extends AbstractWebSocketHandler {

    private final SessionManager sessionManager;
    private final JwtValidator jwtValidator;
    private final AuthenticationGuard authenticationGuard;
    private final WsMessageDispatcher dispatcher;
    private final WebSocketConnectionManager connectionManager;
    private final PendingMessagesHandler pendingMessagesHandler;

    public MyBinaryWebSocketHandler(
            SessionManager sessionManager,
            JwtValidator jwtValidator,
            AuthenticationGuard authenticationGuard,
            WsMessageDispatcher dispatcher,
            WebSocketConnectionManager connectionManager,
            PendingMessagesHandler pendingMessagesHandler) {
        this.sessionManager = sessionManager;
        this.jwtValidator = jwtValidator;
        this.authenticationGuard = authenticationGuard;
        this.dispatcher = dispatcher;
        this.connectionManager = connectionManager;
        this.pendingMessagesHandler = pendingMessagesHandler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        connectionManager.handleConnectionEstablished(session);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        try {
            MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.parseFrom(message.getPayload().array());
            SessionContext context = new SessionContext(session, sessionManager);

            if (!wsMessage.hasAuthMessage()) {
                authenticationGuard.check(context);
            }

            dispatcher.dispatch(context, wsMessage);

            if (wsMessage.hasAuthMessage() && sessionManager.isAuthenticated(session.getId())) {
                String username = sessionManager.getSessionInfo(session.getId()).getUsername();
                sendAllPendingMessages(session, username);
            }
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
        pendingMessagesHandler.sendPendingMessages(session, username);
        pendingMessagesHandler.sendPendingDeletions(session, username);
        pendingMessagesHandler.sendPendingUnblocks(session, username);
        pendingMessagesHandler.sendPendingReadReceipts(session, username);
        pendingMessagesHandler.sendPendingContactIdentities(session, username);
    }

    private void closeSession(WebSocketSession session) {
        try {
            session.close();
            log.info("Sesión cerrada por error - ID sesión: {}", session.getId());
        } catch (Exception e) {
            log.error("Error al cerrar sesión - ID sesión: {}", session.getId(), e);
        }
    }
}
