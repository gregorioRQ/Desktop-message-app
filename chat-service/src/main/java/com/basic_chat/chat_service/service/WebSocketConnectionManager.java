package com.basic_chat.chat_service.service;

import com.basic_chat.chat_service.models.UserPresenceEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

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

    public WebSocketConnectionManager(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void handleConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 1. Obtener la información de la sesión ANTES de eliminarla
        SessionManager.SessionInfo info = sessionManager.getSessionInfo(session.getId());

        // Limpiar la referencia en memoria (el socket físico).
        // El API Gateway es responsable de limpiar la sesión en Redis cuando expira
        // o en la desconexión, por lo que este servicio ya no necesita interactuar
        // con Redis para la limpieza.
        sessionManager.removeSession(session.getId());

        // 2. Si la sesión estaba registrada, publicar evento de desconexión
        /* 
        if (info != null) {
            log.info("Conexión WebSocket cerrada - Sesión: {}, Usuario: {}, Razón: {}",
                    session.getId(), info.getUsername(), status.getReason());
            rabbitTemplate.convertAndSend("user.presence",
                    new UserPresenceEvent(info.getUserId(), info.getUsername(), false, System.currentTimeMillis()));
        } else {
            log.info("Conexión WebSocket cerrada (sin sesión registrada) - ID: {}, Razón: {}",
                    session.getId(), status.getReason());
        }*/
    }

    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Error de transporte en WebSocket - ID sesión: {}", 
                session.getId(), exception);
    }
}
