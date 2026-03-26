package com.pola.config;
// Configuración centralizada de servicios http.

public class HttpConfig {
    // URL del servicio de perfiles
    public static final String PROFILE_SERVICE_URL = "http://localhost:8080/api/v1/";
    
    // Timeouts (en segundos)
    public static final int CONNECT_TIMEOUT = 10;
    public static final int REQUEST_TIMEOUT = 30;
    
    // Headers comunes
    public static final String CONTENT_TYPE_PROTOBUF = "application/x-protobuf";
    public static final String ACCEPT_PROTOBUF = "application/x-protobuf";

    // System Tray - Tiempo de espera antes de cerrar conexión WebSocket (en milisegundos)
    // Cuando el usuario cierra la ventana, se espera este tiempo antes de desconectar el WebSocket
    public static final long TRAY_WS_CLOSE_DELAY_MS = 60_000; // 1 minuto

    private HttpConfig() {
        // Clase de utilidad, no instanciable
    }
}
