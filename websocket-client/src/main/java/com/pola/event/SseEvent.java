package com.pola.event;

import java.time.Instant;
import java.util.Objects;

/**
 * Representa un evento SSE recibido del notification-service.
 * Esta clase es inmutable y contiene el tipo de evento, los datos
 * y metadatos adicionales como el timestamp.
 */
public final class SseEvent {
    private final SseEventType type;
    private final String data;
    private final Instant timestamp;

    /**
     * Constructor privado. Usar el builder.
     */
    private SseEvent(SseEventType type, String data, Instant timestamp) {
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.data = Objects.requireNonNull(data, "Data cannot be null");
        this.timestamp = Objects.requireNonNull(timestamp, "Timestamp cannot be null");
    }

    /**
     * Obtiene el tipo de evento SSE.
     * @return el tipo de evento (PRESENCE, MESSAGE, NOTIFICATION, HEARTBEAT, UNKNOWN)
     */
    public SseEventType getType() {
        return type;
    }

    /**
     * Obtiene los datos del evento.
     * @return los datos como string (generalmente JSON)
     */
    public String getData() {
        return data;
    }

    /**
     * Obtiene el timestamp del evento.
     * @return el instante en que se recibió el evento
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "SseEvent{" +
                "type=" + type +
                ", data='" + (data.length() > 50 ? data.substring(0, 50) + "..." : data) + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }

    /**
     * Builder para crear instancias de SseEvent.
     */
    public static class Builder {
        private SseEventType type;
        private String data;
        private Instant timestamp = Instant.now();

        public Builder type(SseEventType type) {
            this.type = type;
            return this;
        }

        public Builder data(String data) {
            this.data = data;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public SseEvent build() {
            return new SseEvent(type, data, timestamp);
        }
    }
}
