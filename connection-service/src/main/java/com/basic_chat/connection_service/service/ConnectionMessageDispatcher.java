package com.basic_chat.connection_service.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.basic_chat.connection_service.handler.ConnectionWsMessageHandler;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Dispatcher de mensajes WebSocket para connection-service.
 * 
 * Este componente recibe todos los mensajes binarios y los distribuye al handler
 * apropiado según el tipo de mensaje. Implementa el patrón Chain of Responsibility.
 * 
 * Los handlers disponibles son inyectados automáticamente por Spring gracias a
 * la interfaz ConnectionWsMessageHandler.
 * 
 * Patrón de diseño: Chain of Responsibility / Strategy
 */
@Service
@Slf4j
public class ConnectionMessageDispatcher {

    private final List<ConnectionWsMessageHandler> handlers;

    public ConnectionMessageDispatcher(List<ConnectionWsMessageHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * Distribuye el mensaje al handler apropiado.
     * 
     * Itera sobre todos los handlers registrados y busca el primero que suporte
     * el tipo de mensaje recibido. Si no encuentra ningún handler, lanza una
     * excepción indicando que el tipo de mensaje no está soportado.
     * 
     * El orden de los handlers en la lista determina la prioridad de matching.
     * 
     * @param sender Username del usuario que envió el mensaje
     * @param message Mensaje protobuf recibido desde el cliente
     * @throws IllegalArgumentException si ningún handler soporta el tipo de mensaje
     */
    public void dispatch(String sender, MessagesProto.WsMessage message) {
        for (ConnectionWsMessageHandler handler : handlers) {
            if (handler.supports(message)) {
                try {
                    handler.handle(sender, message);
                    return;
                } catch (Exception e) {
                    log.error("Error en handler {} al procesar mensaje de {}: {}", 
                            handler.getClass().getSimpleName(), sender, e.getMessage(), e);
                    throw new RuntimeException("Error procesando mensaje", e);
                }
            }
        }
        
        // Si no se encontró ningún handler, lanzar excepción
        log.warn("Tipo de mensaje no soportado: {}", message.getPayloadCase());
        throw new IllegalArgumentException("Tipo de mensaje no soportado: " + message.getPayloadCase());
    }
}
