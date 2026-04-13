package com.basic_chat.profile_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UserCreatedRabbitConfig {

    public static final String USER_CREATED_QUEUE = "user.created";
    public static final String USER_CREATED_ROUTING_KEY = "user.created";

    @Bean
    public Queue userCreatedQueue() {
        return new Queue(USER_CREATED_QUEUE, true);
    }

    @Bean
    public Binding userCreatedBinding(Queue userCreatedQueue, DirectExchange userLogoutExchange) {
        return BindingBuilder.bind(userCreatedQueue).to(userLogoutExchange).with(USER_CREATED_ROUTING_KEY);
    }
}