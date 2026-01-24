package com.basic_chat.chat_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Service
@Slf4j
public class WebSocketConnectionManager {

    private final SessionManager sessionManager;

    public WebSocketConnectionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void handleConnectionEstablished(WebSocketSession session) {
        sessionManager.registerPendingConnection(session.getId());
        log.info("Nueva conexión WebSocket establecida - ID sesión: {}", session.getId());
    }

    public void handleConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionManager.removeSession(session.getId());
        log.info("Conexión WebSocket cerrada - ID sesión: {}, Razón: {}", 
                session.getId(), status.getReason());
    }

    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Error de transporte en WebSocket - ID sesión: {}", 
                session.getId(), exception);
    }
}