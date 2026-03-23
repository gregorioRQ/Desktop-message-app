package com.pola.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cliente de notificaciones STOMP (LEGACY - COMENTADO).
 * 
 * Este cliente establecía conexión WebSocket STOMP con el servidor de notificaciones.
 * Fue comentario porque:
 * 1. Las notificaciones ahora se reciben vía SSE (SseNotificationClient)
 * 2. La funcionalidad de presencia será implementada después
 * 
 * Mantenido por si se necesita en el futuro para referencia o reutilización.
 * 
 * @deprecated Reemplazado por SseNotificationClient para notificaciones.
 */
public class NotificationService {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    /**
     * Constructor vacío para evitar errores de compilación.
     * El servicio ya no se usa activamente.
     */
    public NotificationService() {
        logger.info("NotificationService (cliente) comentarios - no se ejecutará");
    }

    // private WebSocketStompClient stompClient;
    // private StompSession stompSession;
    // private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    // private final String userId;
    // private final String username;
    // private String token;
    // private Runnable onStompConnected;
    // private BiConsumer<String, Boolean> presenceListener;
    // private Consumer<Throwable> errorListener;
    // private boolean isConnected = false;

    // public NotificationService(String userId, String username) {
    //     this.userId = userId;
    //     this.username = username;
    // }

    // /**
    //  * Establece la conexión STOMP con el servidor de notificaciones.
    //  * @deprecated Reemplazado por SseNotificationClient
    //  */
    // public void connect(String token, String userId) { ... }

    // /**
    //  * Desconecta el cliente STOMP.
    //  * @deprecated No utilizado
    //  */
    // public void disconnect() { ... }

    // /**
    //  * Registra un listener para notificaciones.
    //  * @deprecated No utilizado
    //  */
    // public void addNotificationListener(Consumer<String> listener) { ... }

    // /**
    //  * Configura el listener para errores.
    //  * @deprecated No utilizado
    //  */
    // public void setErrorListener(Consumer<Throwable> listener) { ... }

    // /**
    //  * Configura el listener para presencia.
    //  * @deprecated No utilizado
    //  */
    // public void setPresenceListener(BiConsumer<String, Boolean> listener) { ... }

    // /**
    //  * Envía notificación de agregar contacto.
    //  * @deprecated No utilizado
    //  */
    // public void sendAddContactNotification(String fromUserId, String toUserId) { ... }

    // /**
    //  * Envía notificación de usuario online.
    //  * @deprecated No utilizado
    //  */
    // public void sendUserOnlineNotification(String userId) { ... }

    // /**
    //  * Envía notificación de eliminar contactos.
    //  * @deprecated No utilizado
    //  */
    // public void sendDropContactNotification(String userId, List<String> contactIds) { ... }
}
