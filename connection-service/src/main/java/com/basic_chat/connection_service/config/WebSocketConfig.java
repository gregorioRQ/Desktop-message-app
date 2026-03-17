package com.basic_chat.connection_service.config;

import com.basic_chat.connection_service.handler.ConnectionWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ConnectionWebSocketHandler connectionWebSocketHandler;

    public WebSocketConfig(ConnectionWebSocketHandler connectionWebSocketHandler) {
        this.connectionWebSocketHandler = connectionWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(connectionWebSocketHandler, "/ws")
                .setAllowedOrigins("*");
    }
}
