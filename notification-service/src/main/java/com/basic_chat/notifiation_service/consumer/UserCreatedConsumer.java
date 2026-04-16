package com.basic_chat.notifiation_service.consumer;

import com.basic_chat.notifiation_service.model.UserCreateEvent;
import com.basic_chat.notifiation_service.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de eventos de usuario creado desde RabbitMQ.
 * 
 * Este componente escucha la cola "user.created" donde profile-service
 * publica eventos cuando un nuevo usuario se registra exitosamente.
 * 
 * La responsabilidad es crear el registro del usuario en la tabla de usuarios
 * de notification-service para mantener sincronización.
 */
@Component
public class UserCreatedConsumer {

    private static final Logger logger = LoggerFactory.getLogger(UserCreatedConsumer.class);

    private final UserService userService;

    public UserCreatedConsumer(UserService userService) {
        this.userService = userService;
    }

    /**
     * Procesa un evento de usuario creado recibido desde profile-service.
     * 
     * Este método se ejecuta cuando profile-service publica un evento en la cola
     * "user.created" después de un registro exitoso.
     * 
     * El evento contiene el userId y username que se usan para crear el registro
     * en la tabla de usuarios de notification-service.
     * 
     * @param event Evento de usuario creado con userId y username
     */
    @RabbitListener(queues = "user.created")
    public void handleUserCreatedEvent(UserCreateEvent event) {
        logger.info("Received user created event for userId: {}, username: {}", event.getUser_id(), event.getUsername());

        if (event == null || event.getUser_id() == null || event.getUser_id().isEmpty()) {
            logger.warn("User created event received with empty or null userId. Cannot create user.");
            return;
        }

        try {
            userService.create(event);
            logger.info("User created successfully in notification-service for userId: {}", event.getUser_id());
        } catch (Exception e) {
            logger.error("Failed to create user in notification-service for userId: {}. Error: {}", event.getUser_id(), e.getMessage());
        }
    }
}