package com.pola.controller;

import com.pola.proto.MessagesProto.AuthMessage;
import com.pola.proto.MessagesProto.WsMessage;
import com.pola.service.ContactService;
import com.pola.service.WebSocketService;

import javafx.application.Platform;
import javafx.scene.paint.Color;

/**
 * Helper para manejar acciones de conexión/desconexión.
 * 
 * El servicio de notificaciones STOMP fue comentado.
 * Ahora las notificaciones se manejan vía SseNotificationClient.
 */
public class ConnectionActionHelper {
    private final WebSocketService webSocketService;
    private final ContactService contactService;
    private final ChatController chatController;

    public ConnectionActionHelper(WebSocketService webSocketService, ContactService contactService, ChatController chatController) {
        this.webSocketService = webSocketService;
        this.contactService = contactService;
        this.chatController = chatController;
    }

    public void handleConnect() {
        chatController.showStatus("Conectando...", Color.BLUE);
        
        new Thread(() -> {
            try {
                // Conectar al servicio WebSocket enviando token, userId y username en las cabeceras del handshake
                webSocketService.connect(
                    chatController.getAuthToken(), 
                    chatController.getCurrentUserId(), 
                    chatController.getCurrentUsername()
                );
                
                // El servicio de notificaciones STOMP fue comentado.
                // Ahora las notificaciones se reciben vía SSE cuando el usuario presiona "Conectar SSE"
                // El código relacionado con NotificationService fue comentado.

                Thread.sleep(200);

                Platform.runLater(this::sendAuthMessage);
            } catch (Exception e) {
                Platform.runLater(() -> chatController.showStatus("Error al conectar: " + e.getMessage(), Color.RED));
            }
        }).start();
    }

    public void handleDisconnect() {
        webSocketService.disconnect();
        // El servicio de notificaciones STOMP fue comentado
        // if (chatController.getNotificationService() != null) {
        //     chatController.getNotificationService().disconnect();
        // }
        chatController.updateConnectionStatus(false);
    }

    private void sendAuthMessage() {
        try {
            String token = chatController.getAuthToken();
            if (token == null || token.isEmpty()) {
                chatController.showStatus("Error: Token no disponible", Color.RED);
                return;
            }

            chatController.showStatus("Autenticando...", Color.BLUE);

            AuthMessage authMessage = AuthMessage.newBuilder()
                .setToken(token)
                .build();
                
            WsMessage wsMessage = WsMessage.newBuilder()
                .setAuthMessage(authMessage)
                .build();

            webSocketService.sendMessage(wsMessage);
            System.out.println("Mensaje de autenticacion enviado");
        } catch (Exception e) {
            chatController.showStatus("Error en la autenticación: " + e.getMessage(), null);
        }
    }
}
