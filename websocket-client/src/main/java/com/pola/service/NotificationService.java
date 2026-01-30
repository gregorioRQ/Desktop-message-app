package com.pola.service;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
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
    private final String username;
    private Runnable onStompConnected;
    private BiConsumer<String, Boolean> presenceListener;
    private NotificationHelper helper;

    public NotificationService(String userId, String username) {
        this.userId = userId;
        this.username = username;
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
                String disconnectFrame = "DISCONNECT\n\n\u0000";
                session.getBasicRemote().sendText(disconnectFrame);
                session.close();
            } catch (IOException e) {
                System.err.println("Error al desconectar: " + e.getMessage());
            }
        }
    }

    public void addNotificationListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    public void setPresenceListener(BiConsumer<String, Boolean> listener) {
        this.presenceListener = listener;
    }

    public void setOnStompConnected(Runnable onStompConnected) {
        this.onStompConnected = onStompConnected;
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        this.helper = new NotificationHelper(session);
        System.out.println("Conexión WebSocket establecida. Enviando frame CONNECT de STOMP.");
        helper.sendConnectFrame(this.userId);
    }
    /**
     * Procesa un mensaje 
     * 
     * Flujo:
     * 1. Verifica el tipo de comando STTOMP.
     * 2. Suscribe el usuario actual a los topics
     * @param message
     */

    @OnMessage
    public void onMessage(String message) {
        System.out.println("Mensaje STOMP recibido: " + message);

        if (message.startsWith("CONNECTED")) {
            handleConnectedMessage();
        } else if (message.startsWith("RECEIPT")) {
            handleReceiptMessage();
        } else if (message.startsWith("MESSAGE")) {
            handleStompMessage(message);
        }
    }

    private void handleConnectedMessage() {
        System.out.println("STOMP CONNECTED. Suscribiendo a /topic/notifications/" + userId);

        // Suscribirse a notificaciones
        if (helper != null) {
            helper.sendMessage(helper.buildSubscribeFrame("sub-0", "/topic/notifications/" + userId));

            // Suscribirse a presencia
            helper.sendMessage(helper.buildSubscribeFrame("sub-1", "/queue/presence/" + userId));
        }

        if (onStompConnected != null) {
            onStompConnected.run();
        }
    }

    private void handleReceiptMessage() {
        System.out.println("Confirmación de suscripción recibida exitosamente (RECEIPT frame).");
    }

    private void handleStompMessage(String message) {
        int bodyStart = message.indexOf("\n\n");
        if (bodyStart == -1) {
            System.err.println("Mensaje STOMP inválido: no se encontró el cuerpo.");
            return;
        }

        String headers = message.substring(0, bodyStart);
        String body = message.substring(bodyStart + 2).replace("\u0000", "");

        // Extraer cabeceras relevantes
        String subscriptionId = helper.extractHeader(headers, "subscription");
        String type = helper.extractHeader(headers, "type");

        if (subscriptionId == null || type == null) {
            System.err.println("Mensaje STOMP inválido: faltan cabeceras obligatorias.");
            return;
        }

        // Delegar el procesamiento según el tipo
        switch (type) {
            case "user_online":
                handleUserPresenceMessage(body, true);
                break;
            case "user_offline":
                handleUserPresenceMessage(body, false);
                break;
            case "chat_message":
                handleChatMessage(body);
                break;
            case "contact_added_you":
                handleContactNotification(body);
                break;
            case "contact_blocked":
                handleContactBlockedNotification(body);
                break;
            case "contact_unblocked":
                handleContactUnblockedNotification(body);
                break;
            default:
                handleUnknownMessage(type, body);
                break;
        }
    }

    private void handleUserPresenceMessage(String body, boolean isOnline) {
        System.out.println((isOnline ? "Usuario conectado: " : "Usuario desconectado: ") + body);
        if (helper != null) {
            helper.handlePresenceMessage(body, isOnline, presenceListener);
        }
    }

    private void handleChatMessage(String body) {
        System.out.println("Mensaje de chat recibido: " + body);
        listeners.forEach(listener -> listener.accept(body));
    }

    private void handleContactNotification(String body) {
        System.out.println("Notificación de contacto recibida: " + body);
        listeners.forEach(listener -> listener.accept(body)); // Aquí puedes enviar al panel de notificaciones
    }

    private void handleContactBlockedNotification(String body) {
        System.out.println("Notificación de contacto bloqueado: " + body);
        listeners.forEach(listener -> listener.accept(body));
    }

    private void handleContactUnblockedNotification(String body) {
        System.out.println("Notificación de contacto desbloqueado: " + body);
        listeners.forEach(listener -> listener.accept(body));
    }

    private void handleUnknownMessage(String type, String body) {
        System.err.println("Tipo de mensaje desconocido: " + type + ". Cuerpo: " + body);
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("Conexión de notificaciones cerrada: " + closeReason);
    }
    /**
     * envia al servicio de notificaciones los datos para actualizar los contactos de un usuario.
     * @param fromUserId Id del contacto.
     * @param toUserId  Id del usuario actual.
     */
    public void sendAddContactNotification(String fromUserId, String toUserId) {
        if (helper != null) {
            helper.sendAddContactNotification(fromUserId, toUserId);
        }
    }

    /**
     * Envia al servicio de notificaciones el id para que lo retransmita al remitente.
     * @param userId Id oficial de este usuario que acepto como contacto al remitente.
     */
    public void sendUserCreateNotification(String userId) {
        if (helper != null) {
            helper.sendUserCreateNotification(userId);
        }
    }
    
    public void sendUserOnlineNotification(String userId) {
        if (helper != null) {
            helper.sendUserOnlineNotification(userId);
        }
    }
}
