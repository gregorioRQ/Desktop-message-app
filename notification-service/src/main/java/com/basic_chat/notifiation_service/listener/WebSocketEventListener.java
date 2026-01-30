package com.basic_chat.notifiation_service.listener;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import com.basic_chat.notifiation_service.service.NotificationService;
import com.basic_chat.notifiation_service.service.UserPresenceService;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);
    private final NotificationService notificationService;
    private final UserPresenceService userPresenceService;

    public WebSocketEventListener(NotificationService notificationService, UserPresenceService userPresenceService) {
        this.notificationService = notificationService;
        this.userPresenceService = userPresenceService;
    }

    /**
     * Listener websocket que procesa el evento SessionConnect.
     * 
     * Flujo:
     * Verifica si se halla el user en el Principal.
     * Verifica que el usuario y la sesion no sean nulas.
     * 
     * @param event El evento que ocurre cuando el cliente se conecta a este servicio.
     */

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();
        String sessionId = headerAccessor.getSessionId();
        
        logger.info("Evento CONNECT recibido. SessionId: {}, User: {}", sessionId);

        String userId = null;
        if (user != null) {
            userId = user.getName();
        } else {
            userId = headerAccessor.getFirstNativeHeader("userId");
        }

        if (userId != null && sessionId != null) {
            userPresenceService.handleSessionConnected(userId, sessionId);
        } else {
            logger.warn("Usuario no encontrado en el evento de conexión. No se registrará en Redis.");
        }
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = headerAccessor.getDestination();
        String sessionId = headerAccessor.getSessionId();
        Principal user = headerAccessor.getUser();
        logger.info("Evento SUBSCRIBE recibido. Destination: {}, SessionId: {}, User: {}", destination, sessionId, user);
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        userPresenceService.handleSessionDisconnect(sessionId);
        logger.info("Evento DISCONECT recibido. SessionId: {}", sessionId);
    }
}