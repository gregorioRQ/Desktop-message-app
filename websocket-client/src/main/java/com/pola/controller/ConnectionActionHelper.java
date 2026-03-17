package com.pola.controller;

import com.pola.proto.MessagesProto.AuthMessage;
import com.pola.proto.MessagesProto.WsMessage;
import com.pola.service.ContactService;
import com.pola.service.NotificationService;
import com.pola.service.WebSocketService;

import javafx.application.Platform;
import javafx.scene.paint.Color;

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
                
                // Conectar servicio de notificaciones
                if (chatController.getNotificationService() == null) {
                    NotificationService ns = new NotificationService(
                        chatController.getCurrentUserId(), 
                        chatController.getCurrentUsername()
                    );
                    
                    ns.addNotificationListener(mensaje -> {
                        Platform.runLater(() -> {
                            System.out.println("Notificación recibida: " + mensaje);
                            chatController.showStatus(mensaje, Color.BLUE);
                        });
                    });

                    ns.setPresenceListener((userId, isOnline) -> {
                        Platform.runLater(() -> {
                            contactService.setContactOnline(userId, isOnline);
                            if (isOnline) {
                                contactService.notifyContactWeAreOnline(userId);
                            }
                        });
                    });

                chatController.setNotificationService(ns);
                contactService.setNotificationService(ns);
            }

                chatController.getNotificationService().connect(chatController.getAuthToken(), chatController.getCurrentUserId());

                Thread.sleep(200);

                Platform.runLater(this::sendAuthMessage);
            } catch (Exception e) {
                Platform.runLater(() -> chatController.showStatus("Error al conectar: " + e.getMessage(), Color.RED));
            }
        }).start();
    }

    public void handleDisconnect() {
        webSocketService.disconnect();
        if (chatController.getNotificationService() != null) {
            chatController.getNotificationService().disconnect();
        }
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
