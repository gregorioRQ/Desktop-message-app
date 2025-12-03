package com.basic_chat.chat_service.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/* 
@Configuration
public class RabbitMQconfig {

    private static final String MESSAGE_SENT_QUEUE = "message.sent";

    @Bean
    public Queue chatQueue() {
        return new Queue(MESSAGE_SENT_QUEUE, false);
    }

    @Bean
    public Queue messageReadQueue() {
        // durable = true es recomendable en producción
        return new Queue("message.read", false);
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
*/