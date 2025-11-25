package com.basic_chat.chat_service.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.converter.ProtobufMessageConverter;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Esta clase creará un servidor websocket. 
 * Indica a Spring que use Protobuf en lugar de JSON.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Registrar el endpoint STOMP
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // endpoint para los clientes STOMP
        registry.addEndpoint("/ws-chat").setAllowedOriginPatterns("*").withSockJS();
    }

    // configurar el broker de mensajería.
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // prefijos para enviar y recibir mensajes
        registry.enableSimpleBroker("/topic", "/queue");
        // prefijos para endpoints mejorados.
        registry.setApplicationDestinationPrefixes("/app");
    }

    // 3. Configurar Protobuf como el convertidor de mensajes
    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        messageConverters.add(new ProtobufMessageConverter());
        // Devolvemos true si agregamos/modificamos la lista.
        return true; 
    }
}
