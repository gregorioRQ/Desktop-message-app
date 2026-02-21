package com.pola.config;

public class WebSocketConfig {
    // URL al gateway (chat-service)
    public static final String WS_URL = "ws://localhost:8080/ws-binary";
    
    // URL al gateway (notification-service)
     public static final String NOTIFICATION_WS_URL = "ws://localhost:8080/ws";

    // URL al gateway (media-service)
    public static final String WS_MEDIA_URL = "ws://localhost:8080/ws-media";

    private WebSocketConfig() {
        // Clase de utilidad, no instanciable
    }
}
