package com.basic_chat.notifiation_service.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controlador WebSocket para operaciones de usuario (LEGACY - COMENTADO).
 * 
 * Este controlador manejaba mensajes STOMP para registrar usuarios y notificar
 * presencia online. Fue comentado porque:
 * 1. La funcionalidad de presencia se implementará después
 * 2. No es necesaria para el flujo actual de notificaciones SSE
 * 
 * Mantenido por si se necesita en el futuro para referencia o reutilización.
 * 
 * @deprecated Controlador legacy - no utilizado.
 */
// @Controller
public class UserWebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(UserWebSocketController.class);

    /**
     * Constructor vacío para evitar errores de compilación.
     * El controlador ya no se usa activamente.
     */
    public UserWebSocketController() {
        logger.info("UserWebSocketController comentarios - no se ejecutará");
    }

    // private final UserService userService;
    // private final NotificationService notificationService;
    // private final UserPresenceService userPresenceService;

    // public UserWebSocketController(UserService userService, NotificationService notificationService, UserPresenceService userPresenceService){
    //     this.userService = userService;
    //     this.notificationService = notificationService;
    //     this.userPresenceService = userPresenceService;
    // }

    // /**
    //  * Recibe el mensaje del cliente en el destino: /app/user.add
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // @MessageMapping("/user.add")
    // public void createUser(@Payload UserCreateEvent event) { ... }

    // /**
    //  * Permite al cliente notificar explícitamente que está online.
    //  * @deprecated Metodo legacy - no utilizado
    //  */
    // @MessageMapping("/user.online")
    // public void notifyUserOnline(@Payload UserOnlineEvent event) { ... }
}
