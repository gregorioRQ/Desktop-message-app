package com.pola.util;

import com.pola.controller.ChatController;
import com.pola.model.Session;
import com.pola.service.LogoutService;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;

public class LogoutHandler {

    private final ChatController chatController; // For UI updates (showStatus)
    private final LogoutService logoutService;
    private final LogoutContext logoutContext;
    private final Button logoutButton;

    public LogoutHandler(
            ChatController chatController,
            LogoutService logoutService,
            LogoutContext logoutContext,
            Button logoutButton) {
        this.chatController = chatController;
        this.logoutService = logoutService;
        this.logoutContext = logoutContext;
        this.logoutButton = logoutButton;
    }

    public void handleLogout() {
        if (logoutButton != null) logoutButton.setDisable(true);
        chatController.showStatus("Cerrando sesión...", Color.BLUE);

        Session session = logoutContext.getTokenRepository().loadSession();
        String refreshToken = (session != null) ? session.getRefreshToken() : "";

        logoutService.logout(refreshToken) // Asume que LogoutService no necesita estar en el contexto
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
        if (chatController.getNotificationService() != null) chatController.getNotificationService().disconnect();
        
        if (logoutContext.getMessageService() != null) {
            logoutContext.getMessageService().clearMessages();
            logoutContext.getMessageService().clearAllNotifications();
        }
        if (logoutContext.getContactService() != null) logoutContext.getContactService().clearOnlineUsers();
        logoutContext.getTokenRepository().clearSession();

        Platform.runLater(() -> logoutContext.getViewManager().showLoginView());
    }
}