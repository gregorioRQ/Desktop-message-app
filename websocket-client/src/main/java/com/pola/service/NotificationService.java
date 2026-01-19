package com.pola.service;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

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
    private static final String SERVER_URI = "ws://localhost:8084/ws";

    private Session session;
    private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final String userId;
    private Runnable onStompConnected;

    public NotificationService(String userId) {
        this.userId = userId;
    }

    public void connect() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, URI.create(SERVER_URI));
            System.out.println("Conectando al servidor websocket de notificaciones");
        } catch (Exception e) {
            System.err.println("Error al conectar con el servicio de notificaciones: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                System.err.println("Error al desconectar: " + e.getMessage());
            }
        }
    }

    public void addNotificationListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void setOnStompConnected(Runnable onStompConnected) {
        this.onStompConnected = onStompConnected;
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println("Conexión WebSocket establecida. Enviando frame CONNECT de STOMP.");
        
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
        System.out.println("Mensaje STOMP recibido: " + message);

        if (message.startsWith("CONNECTED")) {
            System.out.println("STOMP CONNECTED. Suscribiendo a /topic/notifications/" + userId);
            // Frame SUBSCRIBE
            String subscribeFrame = "SUBSCRIBE\n" +
                                    "id:sub-0\n" +
                                    "destination:/topic/notifications/" + userId + "\n" +
                                    "receipt:receipt-sub-0\n" +
                                    "\n" +
                                    "\u0000";
            sendMessage(subscribeFrame);

            if (onStompConnected != null) {
                onStompConnected.run();
            }
        } else if (message.startsWith("RECEIPT")) {
            System.out.println("Confirmación de suscripción recibida exitosamente (RECEIPT frame).");
        } else if (message.startsWith("MESSAGE")) {
            // El cuerpo del mensaje está después de la cabecera, separado por una línea en blanco (\n\n)
            int bodyStart = message.indexOf("\n\n");
            if (bodyStart != -1) {
                // +2 para saltar los caracteres de nueva línea
                String body = message.substring(bodyStart + 2).replace("\u0000", "");
                System.out.println("Notificación recibida: " + body);
                listeners.forEach(listener -> listener.accept(body));
            }
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("Conexión de notificaciones cerrada: " + closeReason);
    }

    public void sendAddContactNotification(String fromUserId, String toUserId) {
        if (session != null && session.isOpen()) {
            // Enviamos IDs en lugar de usernames
            String jsonBody = String.format("{\"from\": \"%s\", \"to\": \"%s\"}", fromUserId, toUserId);
            String sendFrame = "SEND\n" +
                               "destination:/app/contact.add\n" +
                               "content-type:application/json\n" +
                               "\n" +
                               jsonBody + "\n" +
                               "\u0000";
            sendMessage(sendFrame);
        }
    }

    public void sendUserCreateNotification(String userId) {
        if (session != null && session.isOpen()) {
            String jsonBody = String.format("{\"user_id\": \"%s\"}", userId);
            String sendFrame = "SEND\n" +
                               "destination:/app/user.add\n" +
                               "content-type:application/json\n" +
                               "\n" +
                               jsonBody + "\n" +
                               "\u0000";
            sendMessage(sendFrame);
        }
    }

    private void sendMessage(String msg) {
        try {
            session.getBasicRemote().sendText(msg);
        } catch (IOException e) {
            System.err.println("Error enviando mensaje STOMP: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
