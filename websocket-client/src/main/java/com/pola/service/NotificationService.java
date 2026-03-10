package com.pola.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.stomp.ConnectionLostException;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.pola.config.WebSocketConfig;

public class NotificationService {

    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private final String userId;
    private final String username;
    private String token;
    private Runnable onStompConnected;
    private BiConsumer<String, Boolean> presenceListener;
    private Consumer<Throwable> errorListener;
    private boolean isConnected = false;

    public NotificationService(String userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    /**
     * Establece la conexión STOMP con el servidor de notificaciones.
     * 
     * Separamos los headers en dos grupos:
     * - WebSocketHttpHeaders: Headers para el handshake HTTP/WebSocket (Authorization)
     * - StompHeaders: Headers para el frame STOMP CONNECT (userId, username)
     * 
     * Esto evita que Tyrus interprete los headers STOMP como autenticación HTTP.
     * 
     * @param token Token de autenticación JWT
     * @param userId ID del usuario
     */
    public void connect(String token, String userId) {
        this.token = token;
        
        try {
            StandardWebSocketClient wsClient = new StandardWebSocketClient();
            
            stompClient = new WebSocketStompClient(wsClient);
            stompClient.setMessageConverter(new StringMessageConverter());
            
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.setPoolSize(2);
            scheduler.setThreadNamePrefix("stomp-heartbeat-");
            scheduler.setWaitForTasksToCompleteOnShutdown(true);
            scheduler.setAwaitTerminationSeconds(5);
            scheduler.initialize();
            stompClient.setTaskScheduler(scheduler);
            
            System.out.println("[NotificationService] Iniciando conexión STOMP al servidor de notificaciones...");
            
            // Headers para el handshake WebSocket (HTTP)
            WebSocketHttpHeaders wsHeaders = new WebSocketHttpHeaders();
            if (token != null && !token.isEmpty()) {
                wsHeaders.set("Authorization", "Bearer " + token.trim());
            }
            
            // Headers para el frame STOMP CONNECT
            StompHeaders stompHeaders = new StompHeaders();
            stompHeaders.add("userId", userId);
            if (username != null && !username.isEmpty()) {
                stompHeaders.add("username", username);
            }
            
            // Usar método con 4 parámetros para separar headers de WebSocket y STOMP
            stompClient.connectAsync(
                WebSocketConfig.NOTIFICATION_WS_URL,
                wsHeaders,
                stompHeaders,
                new NotificationStompHandler()
            );
            
            System.out.println("[NotificationService] Conexión WebSocket en progreso...");
            
        } catch (Exception e) {
            System.err.println("[NotificationService] Error crítico de conexión: " + e.getMessage());
            e.printStackTrace();
            if (errorListener != null) {
                errorListener.accept(e);
            }
        }
    }

    /**
     * Desconecta el cliente STOMP del servidor de notificaciones.
     * Envía frame DISCONNECT y detiene el cliente WebSocket.
     */
    public void disconnect() {
        isConnected = false;
        if (stompSession != null && stompSession.isConnected()) {
            try {
                stompSession.disconnect();
                System.out.println("[NotificationService] Desconexión STOMP enviada");
            } catch (Exception e) {
                System.err.println("[NotificationService] Error al desconectar: " + e.getMessage());
            }
        }
        
        if (stompClient != null) {
            try {
                stompClient.stop();
            } catch (Exception e) {
                System.err.println("[NotificationService] Error al detener cliente STOMP: " + e.getMessage());
            }
        }
    }

    /**
     * Registra un listener para recibir notificaciones de chat.
     * @param listener Consumer que procesa los mensajes recibidos
     */
    public void addNotificationListener(Consumer<String> listener) {
        listeners.add(listener);
    }

    /**
     * Configura el listener para errores de conexión.
     * @param listener Consumer que maneja los errores
     */
    public void setErrorListener(Consumer<Throwable> listener) {
        this.errorListener = listener;
    }

    /**
     * Configura el listener para eventos de presencia (usuario conectado/desconectado).
     * @param listener BiConsumer que recibe (userId, isOnline)
     */
    public void setPresenceListener(BiConsumer<String, Boolean> listener) {
        this.presenceListener = listener;
    }

    /**
     * Configura callback para cuando la conexión STOMP se establece exitosamente.
     * @param onStompConnected Runnable a ejecutar tras conexión exitosa
     */
    public void setOnStompConnected(Runnable onStompConnected) {
        this.onStompConnected = onStompConnected;
    }

    /**
     * Handler interno para manejar eventos de la sesión STOMP.
     * Maneja: conexión, mensajes, errores de transporte y excepciones.
     */
    private class NotificationStompHandler extends StompSessionHandlerAdapter {

        @Override
        public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
            stompSession = session;
            isConnected = true;
            
            long[] heartbeat = connectedHeaders.getHeartbeat();
            System.out.println("[NotificationService] Conexión STOMP establecida.");
            System.out.println("[NotificationService] Heartbeat configurado: servidorenvía cada " + heartbeat[0] + "ms, cliente debe enviar cada " + heartbeat[1] + "ms");
            
            session.subscribe("/topic/notifications/" + userId, this);
            session.subscribe("/queue/presence/" + userId, this);
            
            System.out.println("[NotificationService] Suscrito a /topic/notifications/" + userId);
            System.out.println("[NotificationService] Suscrito a /queue/presence/" + userId);
            
            if (onStompConnected != null) {
                onStompConnected.run();
            }
        }

        @Override
        public void handleException(StompSession session, StompCommand command, 
                StompHeaders headers, byte[] payload, Throwable exception) {
            System.err.println("[NotificationService] STOMP Exception: " + exception.getMessage());
            exception.printStackTrace();
            if (errorListener != null) {
                errorListener.accept(exception);
            }
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            System.err.println("[NotificationService] Error de transporte WebSocket: " + exception.getMessage());
            System.err.println("[NotificationService] Tipo de error: " + exception.getClass().getSimpleName());
            
            if (exception instanceof ConnectionLostException) {
                System.err.println("[NotificationService] ERROR: Conexión perdida por falta de heartbeat o red");
                System.err.println("[NotificationService] El servidor debería detectar esto y limpiar la sesión en Redis");
            }
            
            isConnected = false;
            
            if (errorListener != null) {
                errorListener.accept(exception);
            }
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            String body = (String) payload;
            String type = headers.getFirst("type");
            String subscriptionId = headers.getFirst("subscription");
            
            System.out.println("[NotificationService] Frame recibido - Type: " + type + ", Subscription: " + subscriptionId);
            
            if (type == null || subscriptionId == null) {
                return;
            }
            
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
    }

    private void handleUserPresenceMessage(String body, boolean isOnline) {
        System.out.println((isOnline ? "Usuario conectado: " : "Usuario desconectado: ") + body);
        
        String userId = extractJsonValue(body, "userId");
        if (userId != null && presenceListener != null) {
            presenceListener.accept(userId, isOnline);
        }
    }

    private void handleChatMessage(String body) {
        System.out.println("Mensaje de chat recibido: " + body);
        listeners.forEach(listener -> listener.accept(body));
    }

    private void handleContactNotification(String body) {
        System.out.println("Notificación de contacto recibida: " + body);
        listeners.forEach(listener -> listener.accept(body));
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
        boolean success = extractJsonBoolean(body, "success");
        String message = success ? "Contacto eliminado correctamente." : "No se pudo eliminar el contacto.";
        System.out.println("Notificación de eliminación: " + message);
        listeners.forEach(listener -> listener.accept(message));
    }

    private void handleUnknownMessage(String type, String body) {
        System.err.println("Tipo de mensaje desconocido: " + type + ". Cuerpo: " + body);
    }

    private String extractJsonValue(String json, String key) {
        if (json == null) return null;
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private boolean extractJsonBoolean(String json, String key) {
        if (json == null) return false;
        String pattern = "\"" + key + "\"\\s*:\\s*(true|false)";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
        java.util.regex.Matcher m = p.matcher(json);
        return m.find() && Boolean.parseBoolean(m.group(1));
    }

    public void sendAddContactNotification(String fromUserId, String toUserId) {
        if (stompSession != null && stompSession.isConnected()) {
            String body = String.format("{\"from\": \"%s\", \"to\": \"%s\"}", fromUserId, toUserId);
            stompSession.send("/app/contact.add", body);
        }
    }

    public void sendUserCreateNotification(String userId) {
        if (stompSession != null && stompSession.isConnected()) {
            String body = String.format("{\"user_id\": \"%s\"}", userId);
            stompSession.send("/app/user.add", body);
        }
    }

    public void sendUserOnlineNotification(String userId) {
        if (stompSession != null && stompSession.isConnected()) {
            String body = String.format("{\"userId\": \"%s\"}", userId);
            stompSession.send("/app/user.online", body);
        }
    }

    public void sendDropContactNotification(String userId, List<String> contactIds) {
        if (stompSession != null && stompSession.isConnected()) {
            StringBuilder contactsJson = new StringBuilder("[");
            for (int i = 0; i < contactIds.size(); i++) {
                contactsJson.append("\"").append(contactIds.get(i)).append("\"");
                if (i < contactIds.size() - 1) {
                    contactsJson.append(",");
                }
            }
            contactsJson.append("]");
            
            String body = String.format("{\"userId\": \"%s\", \"contactIds\": %s}", userId, contactsJson.toString());
            stompSession.send("/app/contact.drop", body);
        }
    }
}
