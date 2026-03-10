package com.basic_chat.connection_service.handler;

import com.basic_chat.proto.MessagesProto;

/**
 * Interfaz para handlers de mensajes WebSocket en connection-service.
 * 
 * Cada handler es responsable de procesar un tipo específico de mensaje.
 * El dispatcher usa esta interfaz para encontrar el handler apropiado para cada mensaje.
 * 
 * Patrón de diseño: Strategy/Chain of Responsibility
 */
public interface ConnectionWsMessageHandler {

    /**
     * Determina si este handler puede procesar el mensaje dado.
     * 
     * @param message Mensaje protobuf recibido
     * @return true si este handler puede procesar el mensaje
     */
    boolean supports(MessagesProto.WsMessage message);

    /**
     * Procesa el mensaje recibido.
     * 
     * @param sender Username del usuario que envió el mensaje
     * @param message Mensaje protobuf a procesar
     */
    void handle(String sender, MessagesProto.WsMessage message);
}
