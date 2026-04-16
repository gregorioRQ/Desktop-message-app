package com.basic_chat.notifiation_service.configuration;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQconfig {
    private static final String CONTACT_EVENTS_QUEUE = "contact.events";
    private static final String MESSAGE_NOTIFICATION_QUEUE = "message.notification";
    private static final String USER_CREATED_QUEUE = "user.created";
    private static final String USER_EXCHANGE = "user.exchange";
    private static final String USER_CREATED_ROUTING_KEY = "user.created";

    /**
     * Exchange para eventos de usuario.
     * Compartido con profile-service para eventos de usuario creado y logout.
     */
    @Bean
    public DirectExchange userExchange() {
        return new DirectExchange(USER_EXCHANGE);
    }

    /**
     * Cola para eventos de contacto (confirmaciones).
     * IMPORTANTE: Se usa QueueBuilder para forzar declaración como durable=true
     * y evitar conflictos con otros servicios que puedan declararla con diferente параметр.
     */
    @Bean
    public Queue contactAddQueue() {
        return QueueBuilder.durable(CONTACT_EVENTS_QUEUE).build();
    }

    /**
     * Cola para notificaciones de nuevos mensajes.
     * connection-service publica eventos aquí cuando un usuario recibe un mensaje
     * mientras está offline.
     */
    @Bean
    public Queue messageNotificationQueue() {
        return new Queue(MESSAGE_NOTIFICATION_QUEUE, true);
    }

    /**
     * Cola para eventos de usuario creado.
     * profile-service publica eventos aquí cuando un nuevo usuario se registra.
     * notification-service consume estos eventos para sincronizar su tabla de usuarios.
     */
    @Bean
    public Queue userCreatedQueue() {
        return new Queue(USER_CREATED_QUEUE, true);
    }

    /**
     * Binding entre la cola user.created y el exchange user.exchange.
     * Permite que profile-service publique eventos de usuario creado.
     */
    @Bean
    public Binding userCreatedBinding(Queue userCreatedQueue, DirectExchange userExchange) {
        return BindingBuilder.bind(userCreatedQueue).to(userExchange).with(USER_CREATED_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter jackson2JsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jackson2JsonMessageConverter);
        return template;
    }
}
