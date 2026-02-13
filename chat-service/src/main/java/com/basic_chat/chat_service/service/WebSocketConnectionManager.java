package com.basic_chat.chat_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

/**
 * Gestor de conexiones WebSocket.
 * Responsable de registrar y remover sesiones WebSocket.
 * 
 * NOTA: La validación del token es responsabilidad del API Gateway.
 */
@Service
@Slf4j
public class WebSocketConnectionManager {

    private final SessionManager sessionManager;
    private final RedisSessionService redisSessionService;

    public WebSocketConnectionManager(SessionManager sessionManager, RedisSessionService redisSessionService) {
        this.sessionManager = sessionManager;
        this.redisSessionService = redisSessionService;
    }

    public void handleConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 1. Obtener info antes de eliminar para saber qué usuario era
        SessionManager.SessionInfo info = sessionManager.getSessionInfo(session.getId());
        
        if (info != null) {
            // 2. Limpiar la "fuente de verdad" en Redis
            // Esto asegura que si el socket muere, Redis se entere inmediatamente
            redisSessionService.removeSessionId(info.getUserId());
            log.debug("Sincronización con Redis: Sesión eliminada para usuario {}", info.getUsername());
        }

        // 3. Limpiar la referencia en memoria (el socket físico)
        sessionManager.removeSession(session.getId());
        log.info("Conexión WebSocket cerrada - ID sesión: {}, Razón: {}", 
                session.getId(), status.getReason());
    }

    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Error de transporte en WebSocket - ID sesión: {}", 
                session.getId(), exception);
    }
}
