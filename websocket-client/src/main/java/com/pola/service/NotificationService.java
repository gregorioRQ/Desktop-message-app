package com.pola.service;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;

import com.pola.config.WebSocketConfig;

public class NotificationService extends Endpoint {
    // URL gestionada en WebSocketConfig

    private Session session;
    private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final String userId;
    private final String username;
    private Runnable onStompConnected;
    private BiConsumer<String, Boolean> presenceListener;
    private NotificationHelper helper;
    private Consumer<Throwable> errorListener;

    public NotificationService(String userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public void connect(String token) {
        try {
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .configurator(new ClientEndpointConfig.Configurator() {
                    @Override
                    public void beforeRequest(Map<String, List<String>> headers) {
                        if (token != null && !token.isEmpty()) {
                            System.out.println("Enviando token en Notification WS Header");
                            headers.put("Authorization", java.util.Collections.singletonList("Bearer " + token.trim()));
                        }
                    }
                })
                .build();
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, config, URI.create(WebSocketConfig.NOTIFICATION_WS_URL));
            System.out.println("Conectando al servidor websocket de notificaciones");
        } catch (Exception e) {
            System.err.println("[NotificationService] Error crítico de conexión: " + e.getMessage());
            e.printStackTrace(); // Log para desarrollo
            if (errorListener != null) {
                errorListener.accept(e);
            }
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

    public void setErrorListener(Consumer<Throwable> listener) {
        this.errorListener = listener;
    }

    public void setPresenceListener(BiConsumer<String, Boolean> listener) {
        this.presenceListener = listener;
    }

    public void setOnStompConnected(Runnable onStompConnected) {
        this.onStompConnected = onStompConnected;
    }

    @Override
    public void onOpen(Session session, EndpointConfig config) {
        this.session = session;
        this.helper = new NotificationHelper(session);
        System.out.println("Conexión WebSocket establecida. Enviando frame CONNECT de STOMP.");
        helper.sendConnectFrame(this.userId);
        
        session.addMessageHandler(new MessageHandler.Whole<String>() {
            @Override
            public void onMessage(String message) {
                processMessage(message);
            }
        });
    }
    /**
     * Procesa un mensaje 
     * 
     * Flujo:
     * 1. Verifica el tipo de comando STTOMP.
     * 2. Suscribe el usuario actual a los topics
     * @param message
     */

    private void processMessage(String message) {
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
            case "contact_dropped":
                handleContactDroppedNotification(body);
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

    private void handleContactDroppedNotification(String body) {
        boolean success = false;
        if (helper != null) {
            success = helper.extractJsonBoolean(body, "success");
        }
        String message = success ? "Contacto eliminado correctamente." : "No se pudo eliminar el contacto.";
        System.out.println("Notificación de eliminación: " + message);
        listeners.forEach(listener -> listener.accept(message));
    }

    private void handleUnknownMessage(String type, String body) {
        System.err.println("Tipo de mensaje desconocido: " + type + ". Cuerpo: " + body);
    }

    @Override
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

    public void sendDropContactNotification(String userId, java.util.List<String> contactIds) {
        if (helper != null) {
            helper.sendDropContactNotification(userId, contactIds);
        }
    }
}
