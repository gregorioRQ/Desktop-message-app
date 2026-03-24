package com.pola.util;

import com.pola.controller.ChatController;
import com.pola.model.Session;
import com.pola.service.AuthService;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;

public class LogoutHandler {

    private final ChatController chatController; // For UI updates (showStatus)
    private final AuthService authService;
    private final LogoutContext logoutContext;
    private final Button logoutButton;

    public LogoutHandler(
            ChatController chatController,
            AuthService authService,
            LogoutContext logoutContext,
            Button logoutButton) {
        this.chatController = chatController;
        this.authService = authService;
        this.logoutContext = logoutContext;
        this.logoutButton = logoutButton;
    }

    public void handleLogout() {
        if (logoutButton != null) logoutButton.setDisable(true);
        chatController.showStatus("Cerrando sesión...", Color.BLUE);

        Session session = logoutContext.getTokenRepository().loadSession();
        String refreshToken = (session != null) ? session.getRefreshToken() : "";

        authService.logout(refreshToken)
            .thenAccept(response -> {
                Platform.runLater(() -> {
                    if (response.getSuccess()) {
                        chatController.showStatus("Sesión cerrada exitosamente", Color.GREEN);
                        performLocalLogout();
                    } else {
                        chatController.showStatus("Error al cerrar sesión: " + response.getMessage(), Color.RED);
                        if (logoutButton != null) logoutButton.setDisable(false);
                    }
                });
            })
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    chatController.showStatus("Error de conexión: " + e.getMessage(), Color.RED);
                    if (logoutButton != null) logoutButton.setDisable(false);
                });
                return null;
            });
    }

    private void performLocalLogout() {
        if (logoutContext.getWebSocketService() != null) logoutContext.getWebSocketService().disconnect();

        // Desconectar SSE a notification-service
        if (chatController != null) {
            chatController.disconnectSse();
        }

        if (logoutContext.getMessageService() != null) {
            logoutContext.getMessageService().clearMessages();
            logoutContext.getMessageService().clearAllNotifications();
        }
        if (logoutContext.getContactService() != null) logoutContext.getContactService().clearOnlineUsers();
        logoutContext.getTokenRepository().clearSession();

        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Platform.runLater(() -> logoutContext.getViewManager().showLoginView());
        }).start();
    }
}