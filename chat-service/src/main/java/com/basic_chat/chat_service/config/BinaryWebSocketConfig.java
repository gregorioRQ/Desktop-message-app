package com.basic_chat.chat_service.config;

import com.basic_chat.chat_service.handler.MyBinaryWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/*
 * Configuración para WebSocket binario (sin STOMP)
 */
@Configuration
@EnableWebSocket
public class BinaryWebSocketConfig implements WebSocketConfigurer {

    private final MyBinaryWebSocketHandler binaryWebSocketHandler;

    public BinaryWebSocketConfig(MyBinaryWebSocketHandler binaryWebSocketHandler) {
        this.binaryWebSocketHandler = binaryWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(binaryWebSocketHandler, "/ws-binary")
                .setAllowedOrigins("*");
    }
}
