package com.pola.service;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

@ClientEndpoint
public class NotificationService {
private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);
    
    // URL del servidor WebSocket. Ajusta el puerto si es diferente a 8080.
    private static final String SERVER_URI = "ws://localhost:8080/ws";
    private static final String TOPIC_DESTINATION = "/topic/message.sent";

    private Session session;
    private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public void connect() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, URI.create(SERVER_URI));
        } catch (Exception e) {
            logger.error("Error al conectar con el servicio de notificaciones", e);
        }
    }

    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                logger.error("Error al desconectar", e);
            }
        }
    }

    public void addNotificationListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        logger.info("Conexión WebSocket establecida. Enviando frame CONNECT de STOMP.");
        
        // Frame CONNECT de STOMP 1.1/1.2
        // Desactivamos heart-beat (0,0) para simplificar la implementación manual
        String connectFrame = "CONNECT\n" +
                              "accept-version:1.1,1.0\n" +
                              "heart-beat:0,0\n" +
                              "\n" +
                              "\u0000";
        sendMessage(connectFrame);
    }

    @OnMessage
    public void onMessage(String message) {
        logger.debug("Mensaje STOMP recibido: {}", message);

        if (message.startsWith("CONNECTED")) {
            logger.info("STOMP CONNECTED. Suscribiendo a {}", TOPIC_DESTINATION);
            // Frame SUBSCRIBE
            String subscribeFrame = "SUBSCRIBE\n" +
                                    "id:sub-0\n" +
                                    "destination:" + TOPIC_DESTINATION + "\n" +
                                    "\n" +
                                    "\u0000";
            sendMessage(subscribeFrame);
        } else if (message.startsWith("MESSAGE")) {
            // El cuerpo del mensaje está después de la cabecera, separado por una línea en blanco (\n\n)
            int bodyStart = message.indexOf("\n\n");
            if (bodyStart != -1) {
                // +2 para saltar los caracteres de nueva línea
                String body = message.substring(bodyStart + 2).replace("\u0000", "");
                logger.info("Notificación recibida: {}", body);
                listeners.forEach(listener -> listener.accept(body));
            }
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        logger.info("Conexión de notificaciones cerrada: {}", closeReason);
    }

    private void sendMessage(String msg) {
        try {
            session.getBasicRemote().sendText(msg);
        } catch (IOException e) {
            logger.error("Error enviando mensaje STOMP", e);
        }
    }
}
