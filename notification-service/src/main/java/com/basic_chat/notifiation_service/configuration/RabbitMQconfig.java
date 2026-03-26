package com.basic_chat.notifiation_service.configuration;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQconfig {
    private static final String CONTACT_EVENTS_QUEUE = "contact.events";
    private static final String MESSAGE_NOTIFICATION_QUEUE = "message.notification";

    @Bean
    public Queue contactAddQueue() {
        return new Queue(CONTACT_EVENTS_QUEUE, false);
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
