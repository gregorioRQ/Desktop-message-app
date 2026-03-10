package com.basic_chat.connection_service.service;

import com.basic_chat.connection_service.config.RabbitMQConfig;
import com.basic_chat.connection_service.models.RoutedMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class RabbitMQProducerService {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMQProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Envía un mensaje a la cola de mensajes offline.
     * 
     * La cola offline es consumida por TODAS las instancias de chat-service.
     * Se usa cuando el destinatario está offline o no existe en el sistema.
     * 
     * @param message Mensaje enrutado que contiene remitente, destinatario y datos
     */
    public void sendToOfflineQueue(RoutedMessage message) {
        log.info("Encolando mensaje offline para {}", message.getRecipient());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.MESSAGE_EXCHANGE,
                RabbitMQConfig.OFFLINE_ROUTING_KEY,
                message
        );
    }

    /**
     * Envía un mensaje a una cola específica de una instancia.
     * 
     * Se usa cuando el destinatario está conectado en otra instancia.
     * La routing key contiene el ID de la instancia destino.
     * 
     * @param routingKey ID de la instancia destino (ej: "instance-1")
     * @param message Mensaje enrutado que contiene remitente, destinatario y datos
     */
    public void sendToQueue(String routingKey, RoutedMessage message) {
        log.info("Encolando mensaje para {} en instancia {}", message.getRecipient(), routingKey);
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.MESSAGE_EXCHANGE,
                routingKey,
                message
        );
    }
}
