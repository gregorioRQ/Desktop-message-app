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

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);
    private final NotificationService notificationService;

    public WebSocketEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

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
            notificationService.handleSessionConnected(userId, sessionId);
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
        notificationService.handleSessionDisconnect(sessionId);
        logger.info("Evento DISCONECT recibido. SessionId: {}", sessionId);
    }
}