package com.basic_chat.connection_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${connection.service.instance.id}")
    private String instanceId;

    public static final String MESSAGE_EXCHANGE = "message.exchange";
    public static final String MESSAGE_SENT_QUEUE = "message.sent.";
    public static final String MESSAGE_OFFLINE_QUEUE = "message.offline";
    public static final String OFFLINE_ROUTING_KEY = "offline";

    @Bean
    public DirectExchange messageExchange() {
        return new DirectExchange(MESSAGE_EXCHANGE);
    }

    @Bean
    public Queue messageSentQueue() {
        String queueName = MESSAGE_SENT_QUEUE + instanceId;
        return new Queue(queueName, true);
    }

    @Bean
    public Queue messageOfflineQueue() {
        return new Queue(MESSAGE_OFFLINE_QUEUE, true);
    }

    @Bean
    public Binding messageSentBinding(Queue messageSentQueue, DirectExchange messageExchange) {
        return BindingBuilder.bind(messageSentQueue).to(messageExchange).with(instanceId);
    }

    @Bean
    public Binding messageOfflineBinding(Queue messageOfflineQueue, DirectExchange messageExchange) {
        return BindingBuilder.bind(messageOfflineQueue).to(messageExchange).with(OFFLINE_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
