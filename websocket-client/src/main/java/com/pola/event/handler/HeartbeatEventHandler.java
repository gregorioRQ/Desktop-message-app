package com.pola.event.handler;

import com.pola.event.SseEvent;
import com.pola.event.SseEventHandler;
import com.pola.event.SseEventType;

/**
 * Handler para eventos de heartbeat (keep-alive).
 * Procesa eventos SSE de tipo HEARTBEAT.
 * Estos eventos se usan para mantener la conexión activa
 * y no requieren procesamiento especial más allá del logging.
 */
public class HeartbeatEventHandler implements SseEventHandler {

    @Override
    public boolean canHandle(SseEventType type) {
        return type == SseEventType.HEARTBEAT;
    }

    @Override
    public void handle(SseEvent event) {
        if (event == null) {
            return;
        }

        // Los heartbeats solo se loguean, no requieren procesamiento
        String data = event.getData();
        if (":ok".equals(data) || data.contains("heartbeat")) {
            // Heartbeat normal, no loguear para evitar spam
        } else {
            System.out.println("[HeartbeatEventHandler] Heartbeat recibido: " + data);
        }
    }
}
