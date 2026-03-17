package com.basic_chat.chat_service.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.basic_chat.chat_service.handler.OfflineMessageHandler;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Dispatcher de mensajes offline para chat-service.
 * 
 * Este componente recibe todos los mensajes de la cola "message.offline" y los distribuye
 * al handler apropiado según el tipo de mensaje. Implementa el patrón Chain of Responsibility.
 */
@Service
@Slf4j
public class OfflineMessageDispatcher {

    private final List<OfflineMessageHandler> handlers;

    public OfflineMessageDispatcher(List<OfflineMessageHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * Distribuye el mensaje offline al handler apropiado.
     * 
     * Itera sobre todos los handlers registrados y busca el primero que soporte
     * el tipo de mensaje recibido.
     * 
     * @param message Mensaje protobuf recibido desde la cola offline
     * @param recipient Username del destinatario
     * @throws Exception si ningún handler soporta el tipo de mensaje
     */
    public void dispatch(MessagesProto.WsMessage message, String recipient) throws Exception {
        for (OfflineMessageHandler handler : handlers) {
            if (handler.supports(message)) {
                handler.handleOffline(message, recipient);
                return;
            }
        }
        
        log.warn("No handler encontrado para mensaje offline tipo: {}", message.getPayloadCase());
    }
}
