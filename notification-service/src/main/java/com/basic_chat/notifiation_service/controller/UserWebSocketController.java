package com.basic_chat.notifiation_service.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import com.basic_chat.notifiation_service.model.UserCreateEvent;
import com.basic_chat.notifiation_service.model.UserOnlineEvent;
import com.basic_chat.notifiation_service.service.NotificationService;
import com.basic_chat.notifiation_service.service.UserService;

@Controller
public class UserWebSocketController {
    private final UserService userService;
    private final NotificationService notificationService;

    public UserWebSocketController(UserService userService, NotificationService notificationService){
        this.userService = userService;
        this.notificationService = notificationService;
    }

    /**
     * Recibe el mensaje del cliente en el destino: /app/user.add
     * Payload esperado: JSON compatible con UserCreateEvent {"user_id": "user_id"}
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
        notificationService.notifyUserOnline(event.getUserId());
    }
}
