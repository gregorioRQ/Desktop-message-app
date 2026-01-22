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
    private static final String MESSAGE_SENT_QUEUE = "message.sent";
    private static final String MESSAGE_READ_QUEUE = "message.read";
    private static final String USER_ONLINE_QUEUE = "user.online";

    @Bean
    public Queue contactAddQueue() {
        return new Queue(CONTACT_EVENTS_QUEUE, false);
    }

    @Bean
    public Queue userOnlineQueue() {
        return new Queue(USER_ONLINE_QUEUE, false);
    }

    @Bean
    Queue messageSentQueue() {
        return new Queue(MESSAGE_SENT_QUEUE, false);
    }

    @Bean
    public Queue messageReadQueue() {
        return new Queue(MESSAGE_READ_QUEUE, false);
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
