package com.basic_chat.chat_service.config;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;


/* 
public class ChatWebSocketHandler extends BinaryWebSocketHandler{
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        // Convertir binario → Protobuf
        MensajeOuterClass.Mensaje msg =
                MensajeOuterClass.Mensaje.parseFrom(message.getPayload().array());

        System.out.println("Mensaje recibido: " + msg.getContenido());

        // reenviar a todos
        for (WebSocketSession s : sessions.values()) {
            if (s.isOpen()) {
                byte[] bytes = msg.toByteArray();
                s.sendMessage(new BinaryMessage(bytes));
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessions.remove(session.getId());
    }
}
*/