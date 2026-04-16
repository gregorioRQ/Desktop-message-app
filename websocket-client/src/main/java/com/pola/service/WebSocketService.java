package com.pola.service;

import java.util.function.Consumer;

import com.google.protobuf.Message;
import com.pola.proto.MessagesProto.WsMessage;



/**
 * Interface para el servicio WebSocket
 * Principio SOLID: Dependency Inversion - Los clientes dependen de esta abstracción
 */
public interface WebSocketService {
    /**
     * Conecta al servidor WebSocket enviando token de autenticación y datos de usuario
     * en las cabeceras del handshake WebSocket para validación en el servidor
     * 
     * @param token JWT access token para autenticación
     * @param userId ID único del usuario actual
     * @param username Nombre de usuario actual
     */
    void connect(String token, String userId, String username);
    
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
    void sendMessage(Message message);
    
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

    /**
     * Registra un listener para cuando la autenticación es exitosa
     * @param listener Consumer que recibirá el userId
     */
    void setAuthSuccessListener(Consumer<String> listener);
}
