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
     * Maneja el evento de conexión del cliente WebSocket.
     * Este método se ejecuta cuando un cliente establece conexión STOMP.
     * 
     * Proceso:
     * 1. Extrae el Principal establecido por WebSocketConfig (contiene el userId)
     * 2. Si no existe Principal, intenta leer el header "userId" del frame STOMP
     * 3. Extrae el username de los session attributes
     * 4. Registra la sesión en Redis mediante UserPresenceService (incluye mapeo username->userId)
     * 
     * @param event Evento de conexión de sesión STOMP
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = headerAccessor.getUser();
        String sessionId = headerAccessor.getSessionId();
        
        logger.info("Evento CONNECT recibido. SessionId: {}, User: {}", sessionId, user);
        
        // Obtener userId del Principal (establecido por WebSocketConfig)
        String userId = null;
        if (user != null) {
            userId = user.getName();
            logger.debug("userId obtenido del Principal: {}", userId);
        } else {
            userId = headerAccessor.getFirstNativeHeader("userId");
            logger.debug("userId obtenido del header STOMP: {}", userId);
        }
        
        // Obtener username de los session attributes (guardado por WebSocketConfig)
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        logger.debug("username obtenido de session attributes: {}", username);

        if (userId != null && sessionId != null) {
            // Pasar username al servicio de presencia para crear mapeo username->userId en Redis
            userPresenceService.handleSessionConnected(userId, username, sessionId);
            logger.info("Sesión conectada - userId: {}, username: {}, sessionId: {}", userId, username, sessionId);
        } else {
            logger.warn("Usuario no encontrado en el evento de conexión. No se registrará en Redis.");
        }
    }

    /**
     * Maneja el evento de suscripción del cliente.
     * Se ejecuta cuando el cliente se suscribe a un topic o queue.
     * Útil para logging y debugging de suscripciones activas.
     * 
     * @param event Evento de suscripción de sesión STOMP
     */
    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = headerAccessor.getDestination();
        String sessionId = headerAccessor.getSessionId();
        Principal user = headerAccessor.getUser();
        logger.info("Evento SUBSCRIBE recibido. Destination: {}, SessionId: {}, User: {}", destination, sessionId, user);
    }

    /**
     * Maneja el evento de desconexión del cliente WebSocket.
     * 
     * Este método se ejecuta cuando una sesión STOMP se cierra. Puede ser por:
     * - El cliente envía frame DISCONNECT explícitamente
     * - El servidor cierra la conexión por falta de heartbeat
     * - La conexión TCP se pierde abruptamente (red)
     * - El cliente cierra la aplicación sin desconectar
     * 
     * En todos los casos, se limpia la sesión de Redis para mantener consistencia.
     * 
     * @param event Evento de desconexión de sesión STOMP
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        // Obtener información adicional sobre la causa de la desconexión
        int closeStatus = event.getCloseStatus() != null ? event.getCloseStatus().getCode() : -1;
        String closeReason = event.getCloseStatus() != null ? event.getCloseStatus().getReason() : "unknown";
        
        logger.info("Evento DISCONNECT recibido. SessionId: {}, CloseStatus: {}, Reason: {}", 
            sessionId, closeStatus, closeReason);
        
        // Determinar si fue una desconexión limpia o por error/heartbeat
        if (closeStatus == 1000 || closeStatus == 1001) {
            // 1000 = CLOSE_NORMAL, 1001 = GOING_AWAY - desconexión esperada
            logger.info("Desconexión limpia (cliente envió DISCONNECT) para sesión: {}", sessionId);
            userPresenceService.handleSessionDisconnect(sessionId);
        } else if (closeStatus > 0) {
            // Otros códigos indican cierre anormal (posiblemente heartbeat o pérdida de red)
            logger.warn("Desconexión anormal detectada para sesión: {}. Código: {}, Razón: {}. "
                + "Esto puede indicar falla de heartbeat, pérdida de conexión o cierre abrupto del cliente.", 
                sessionId, closeStatus, closeReason);
            userPresenceService.handleSessionExpired(sessionId);
        } else {
            // Sin código de cierre disponible - el cliente probablemente se cerró abruptamente
            // o hubo una pérdida de conexión sin handshake de cierre
            logger.warn("Desconexión sin código de cierre para sesión: {}. "
                + "El cliente probablemente se cerró abruptamente. Limpiando Redis...", sessionId);
            userPresenceService.handleSessionExpired(sessionId);
        }
        
        logger.info("Procesamiento de desconexión completado para sesión: {}", sessionId);
    }
}