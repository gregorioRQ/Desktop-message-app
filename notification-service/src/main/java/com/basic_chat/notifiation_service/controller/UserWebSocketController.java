
/**
 * UserWebSocketController gestiona eventos WebSocket relacionados con usuarios.
 * Permite la creación de nuevos usuarios y la notificación de presencia en línea,
 * recibiendo mensajes del cliente y delegando la lógica a los servicios correspondientes.
 * Facilita la integración de la gestión de usuarios y presencia en tiempo real.
 */
package com.basic_chat.notifiation_service.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import com.basic_chat.notifiation_service.model.UserCreateEvent;
import com.basic_chat.notifiation_service.model.UserOnlineEvent;
import com.basic_chat.notifiation_service.service.NotificationService;
import com.basic_chat.notifiation_service.service.UserPresenceService;
import com.basic_chat.notifiation_service.service.UserService;

@Controller
public class UserWebSocketController {
    private final UserService userService;
    private final NotificationService notificationService;
    private final UserPresenceService userPresenceService;

    public UserWebSocketController(UserService userService, NotificationService notificationService, UserPresenceService userPresenceService){
        this.userService = userService;
        this.notificationService = notificationService;
        this.userPresenceService = userPresenceService;
    }

    /**
     * Recibe el mensaje del cliente en el destino: /app/user.add
     * Payload esperado: JSON compatible con UserCreateEvent {"user_id": "contact_id"}
     */
    @MessageMapping("/user.add")
    public void createUser(@Payload UserCreateEvent event){
        userService.create(event);
    }

    /**
     * Permite al cliente notificar explícitamente que está online.
     * Destino final: /app/user.online
     */
    @MessageMapping("/user.online")
    public void notifyUserOnline(@Payload UserOnlineEvent event){
        userPresenceService.notifyUserOnline(event.getUserId());
    }
}
