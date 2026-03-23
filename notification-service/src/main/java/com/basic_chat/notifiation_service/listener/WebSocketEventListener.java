package com.basic_chat.notifiation_service.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener de eventos WebSocket STOMP (LEGACY - COMENTADO).
 * 
 * Este listener manejaba los eventos de conexión, suscripción y desconexión
 * de sesiones WebSocket STOMP. Fue comentado porque:
 * 1. Interfería con las sesiones de connection-service
 * 2. La funcionalidad SSE no requiere este tipo de manejo de eventos
 * 
 * Mantenido por si se necesita en el futuro para referencia o reutilización.
 * 
 * @deprecated Reemplazado por manejo de conexiones SSE en SseNotificationController.
 */
// @Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    /**
     * Constructor vacío para evitar errores de compilación.
     * El listener ya no se usa activamente.
     */
    public WebSocketEventListener() {
        logger.info("WebSocketEventListener comentarios - no se ejecutará");
    }

    // private final NotificationService notificationService;
    // private final UserPresenceService userPresenceService;

    // public WebSocketEventListener(NotificationService notificationService, UserPresenceService userPresenceService) {
    //     this.notificationService = notificationService;
    //     this.userPresenceService = userPresenceService;
    // }

    // /**
    //  * Maneja el evento de conexión del cliente WebSocket.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // @EventListener
    // public void handleWebSocketConnectListener(SessionConnectEvent event) { ... }

    // /**
    //  * Maneja el evento de suscripción del cliente.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // @EventListener
    // public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) { ... }

    // /**
    //  * Maneja el evento de desconexión del cliente WebSocket.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // @EventListener
    // public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) { ... }
}
