package com.basic_chat.chat_service.handler;

import com.basic_chat.proto.MessagesProto;

/**
 * Interfaz para handlers de mensajes offline.
 * 
 * Cada handler procesa un tipo específico de mensaje que llega a través de la cola
 * "message.offline" cuando el destinatario está offline.
 */
public interface OfflineMessageHandler {

    /**
     * Determina si este handler puede procesar el mensaje offline.
     * 
     * @param message Mensaje protobuf recibido
     * @return true si este handler puede procesar el mensaje
     */
    boolean supports(MessagesProto.WsMessage message);

    /**
     * Procesa el mensaje offline.
     * 
     * @param message Mensaje protobuf a procesar
     * @param recipient Username del destinatario (para quien se guarda el pendiente)
     */
    void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception;
}
