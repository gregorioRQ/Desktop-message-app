package com.pola.service;

import java.util.function.Consumer;

import com.pola.proto2.MessagesProto.WsMessage;

/**
 * Interface para el servicio WebSocket
 * Principio SOLID: Dependency Inversion - Los clientes dependen de esta abstracción
 */
public interface WebSocketService {
    /**
     * Conecta al servidor WebSocket
     */
    void connect();
    
    /**
     * Desconecta del servidor WebSocket
     */
    void disconnect();
    
    /**
     * Verifica si está conectado
     */
    boolean isConnected();
    
    /**
     * Envía un mensaje al servidor
     * @param message Mensaje a enviar
     */
    void sendMessage(WsMessage message);
    
    /**
     * Registra un listener para mensajes entrantes
     * @param listener Consumer que procesará los mensajes
     */
    void setMessageListener(Consumer<WsMessage> listener);
    
    /**
     * Registra un listener para el estado de conexión
     * @param listener Consumer que procesará cambios de conexión
     */
    void setConnectionListener(Consumer<Boolean> listener);
    
    /**
     * Registra un listener para errores
     * @param listener Consumer que procesará errores
     */
    void setErrorListener(Consumer<Throwable> listener);
}
