package com.pola.event;

import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;

/**
 * Bus de eventos SSE implementado como Singleton.
 * Permite registrar handlers para tipos específicos de eventos y publicar eventos
 * que serán entregados a todos los handlers registrados.
 */
public class SseEventBus {
    private static volatile SseEventBus instance;
    private final EnumMap<SseEventType, List<SseEventHandler>> handlers;

    private SseEventBus() {
        this.handlers = new EnumMap<>(SseEventType.class);
        // Inicializar listas para cada tipo de evento
        for (SseEventType type : SseEventType.values()) {
            handlers.put(type, new CopyOnWriteArrayList<>());
        }
    }

    /**
     * Obtiene la instancia singleton del EventBus.
     * @return la instancia del EventBus
     */
    public static SseEventBus getInstance() {
        if (instance == null) {
            synchronized (SseEventBus.class) {
                if (instance == null) {
                    instance = new SseEventBus();
                }
            }
        }
        return instance;
    }

    /**
     * Registra un handler para un tipo específico de evento.
     * @param type el tipo de evento que el handler puede procesar
     * @param handler el handler a registrar
     * @throws IllegalArgumentException si el handler es null
     */
    public void registerHandler(SseEventType type, SseEventHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Handler cannot be null");
        }
        List<SseEventHandler> typeHandlers = handlers.get(type);
        if (!typeHandlers.contains(handler)) {
            typeHandlers.add(handler);
        }
    }

    /**
     * Desregistra un handler para un tipo específico de evento.
     * @param type el tipo de evento
     * @param handler el handler a desregistrar
     */
    public void unregisterHandler(SseEventType type, SseEventHandler handler) {
        List<SseEventHandler> typeHandlers = handlers.get(type);
        typeHandlers.remove(handler);
    }

    /**
     * Publica un evento. El evento será entregado a todos los handlers
     * registrados para el tipo de evento correspondiente.
     * @param event el evento a publicar
     * @throws IllegalArgumentException si el evento es null
     */
    public void publish(SseEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Event cannot be null");
        }

        List<SseEventHandler> typeHandlers = handlers.get(event.getType());

        // Ejecutar handlers en el thread de JavaFX para actualizaciones de UI
        Platform.runLater(() -> {
            for (SseEventHandler handler : typeHandlers) {
                try {
                    handler.handle(event);
                } catch (Exception e) {
                    System.err.println("[SseEventBus] Error handling event " + event.getType() + ": " + e.getMessage());
                }
            }
        });
    }

    /**
     * Desregistra todos los handlers.
     * Útil para limpiar al cerrar la aplicación.
     */
    public void clear() {
        for (List<SseEventHandler> typeHandlers : handlers.values()) {
            typeHandlers.clear();
        }
    }
}
