package com.pola.event;

/**
 * Enumeración de los tipos de eventos SSE soportados.
 * Define los diferentes tipos de eventos que pueden ser recibidos
 * desde el notification-service.
 */
public enum SseEventType {
    PRESENCE,
    CONTACT_LIST,
    MESSAGE,
    NOTIFICATION,
    HEARTBEAT,
    UNKNOWN
}
