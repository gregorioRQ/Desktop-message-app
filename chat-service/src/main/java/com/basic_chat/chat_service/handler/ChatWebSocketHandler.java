package com.basic_chat.chat_service.handler;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import com.basic_chat.proto.PaqueteDatos;
import com.basic_chat.proto.PaqueteDatos.Tipo;
import com.google.protobuf.InvalidProtocolBufferException;

@Component
public class ChatWebSocketHandler extends BinaryWebSocketHandler {

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("New WebSocket connection established: " + session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println("WebSocket connection closed: " + session.getId() + ", status: " + status);
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        try {
            PaqueteDatos chatMessage = PaqueteDatos.parseFrom(message.getPayload());

            LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(chatMessage.getTimestamp()), 
                ZoneId.systemDefault()
            );
            
            System.out.println("\n=== Mensaje Recibido ===");
            System.out.println("Usuario ID: " + chatMessage.getUsuarioId());
            System.out.println("Contenido: " + chatMessage.getContenido());
            System.out.println("Tipo: " + chatMessage.getTipo().name());
            System.out.println("Timestamp: " + dateTime.format(formatter));
            System.out.println("========================\n");

            PaqueteDatos responseMessage = processMessage(chatMessage);
            if (responseMessage != null) {
                broadcast(responseMessage);
            }

        } catch (InvalidProtocolBufferException e) {
            System.err.println("Error parsing protobuf message: " + e.getMessage());
        }
    }

    private PaqueteDatos processMessage(PaqueteDatos chatMessage) {
        switch (chatMessage.getTipo()) {
            case CHAT:
                return chatMessage;
                
            case LOGIN:
                System.out.println(">>> Usuario " + chatMessage.getUsuarioId() + " se ha conectado");
                return PaqueteDatos.newBuilder()
                        .setUsuarioId("SISTEMA")
                        .setContenido(chatMessage.getUsuarioId() + " se ha unido al chat")
                        .setTimestamp(System.currentTimeMillis())
                        .setTipo(Tipo.ALERTA)
                        .build();
                
            case ALERTA:
                System.out.println(">>> Alerta recibida: " + chatMessage.getContenido());
                return chatMessage;
                
            default:
                System.out.println(">>> Tipo de mensaje desconocido: " + chatMessage.getTipo());
                // Return an alert to the sender? For now, just broadcast it.
                return chatMessage;
        }
    }

    public void broadcast(PaqueteDatos message) {
        BinaryMessage binaryMessage = new BinaryMessage(message.toByteArray());
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(binaryMessage);
                }
            } catch (IOException e) {
                System.err.println("Error broadcasting message to session " + session.getId() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        System.err.println("Transport error for session " + session.getId() + ": " + exception.getMessage());
    }
}