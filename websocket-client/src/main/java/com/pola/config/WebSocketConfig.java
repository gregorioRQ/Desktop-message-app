package com.pola.config;

public class WebSocketConfig {
    // URL al gateway (connection-service - mensajes)
    public static final String WS_URL = "ws://localhost:8080/ws";

    // URL al gateway (notification-service)
    public static final String NOTIFICATION_WS_URL = "ws://localhost:8080/ws-notifications";

    // TODO: MEDIA - Reactivar cuando se implemente funcionalidad de envío de imágenes
    // // URL al gateway (media-service)
    // public static final String WS_MEDIA_URL = "ws://localhost:8080/ws-media";

    private WebSocketConfig() {
        // Clase de utilidad, no instanciable
    }
}
