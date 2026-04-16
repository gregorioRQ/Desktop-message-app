package com.pola.event;

/**
 * Interfaz para manejadores de eventos SSE.
 * Cada implementación debe ser responsable de un tipo específico de evento.
 */
public interface SseEventHandler {
    /**
     * Verifica si este handler puede manejar el tipo de evento especificado.
     * @param type el tipo de evento a verificar
     * @return true si este handler puede procesar el tipo de evento
     */
    boolean canHandle(SseEventType type);

    /**
     * Procesa el evento SSE.
     * @param event el evento a procesar
     * @throws IllegalArgumentException si el evento es null
     */
    void handle(SseEvent event);
}
