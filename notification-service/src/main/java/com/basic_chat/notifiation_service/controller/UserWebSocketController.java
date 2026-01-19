package com.basic_chat.notifiation_service.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import com.basic_chat.notifiation_service.model.UserCreateEvent;
import com.basic_chat.notifiation_service.service.UserService;

@Controller
public class UserWebSocketController {
    private final UserService userService;

    public UserWebSocketController(UserService userService){
        this.userService = userService;
    }

    /**
     * Recibe el mensaje del cliente en el destino: /app/user.add
     * Payload esperado: JSON compatible con UserCreateEvent {"user_id": "user_id"}
     */
    @MessageMapping("/user.add")
    public void createUser(@Payload UserCreateEvent event){
        userService.create(event);
    }
}
