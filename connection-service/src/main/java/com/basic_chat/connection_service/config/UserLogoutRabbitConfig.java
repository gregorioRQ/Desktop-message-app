package com.basic_chat.connection_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserLogoutRabbitConfig {

    public static final String USER_LOGOUT_EXCHANGE = "user.exchange";
    public static final String USER_LOGOUT_QUEUE = "user.logout";
    public static final String USER_LOGOUT_ROUTING_KEY = "user.logout";

    @Bean
    public DirectExchange userLogoutExchange() {
        return new DirectExchange(USER_LOGOUT_EXCHANGE);
    }

    @Bean
    public Queue userLogoutQueue() {
        return new Queue(USER_LOGOUT_QUEUE, true);
    }

    @Bean
    public Binding userLogoutBinding(Queue userLogoutQueue, DirectExchange userLogoutExchange) {
        return BindingBuilder.bind(userLogoutQueue).to(userLogoutExchange).with(USER_LOGOUT_ROUTING_KEY);
    }

    @Bean
    public MessageConverter userLogoutJsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate userLogoutRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(userLogoutJsonMessageConverter());
        return template;
    }
}
