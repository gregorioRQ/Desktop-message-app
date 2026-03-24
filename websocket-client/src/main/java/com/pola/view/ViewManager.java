package com.pola.view;

import java.io.IOException;

import com.pola.controller.ChatController;
import com.pola.controller.LoginController;
import com.pola.controller.RegisterController;
import com.pola.database.DatabaseManager;
import com.pola.service.AuthService;
import com.pola.service.ContactService;
import com.pola.service.HttpService;
import com.pola.service.HttpServiceImpl;
import com.pola.service.MessageService;
import com.pola.service.WebSocketService;
import com.pola.service.WebSocketServiceImpl;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Gestor de vistas de la aplicación.
 * Encargado de la navegación entre vistas y gestión del Stage principal.
 */
public class ViewManager {
    private final Stage stage;
    private WebSocketService webSocketService;
    private MessageService messageService;
    private final HttpService httpService;
    private AuthService authService;
    private ContactService contactService;
    private SystemTrayManager systemTrayManager;
    private ChatController chatController;
    private boolean isInChatView = false;

    public ViewManager(Stage stage) {
        this.stage = stage;
        this.httpService = new HttpServiceImpl();
        configureStage();
    }

    /**
     * Configura el Stage principal de la aplicación.
     * Establece el título, tamaño y comportamiento al cerrar la ventana.
     */
    private void configureStage() {
        stage.setTitle("MSG Desktop");
        stage.setWidth(800);
        stage.setHeight(600);
        stage.setOnCloseRequest(event -> {
            if (isInChatView && chatController != null) {
                event.consume();
                hideWindow();
            } else {
                Platform.exit();
            }
        });
    }

    /**
     * Asigna el gestor de System Tray para manejar la bandeja del sistema.
     * Configura los callbacks para comunicación entre el tray y la vista.
     * @param systemTrayManager Instancia del gestor de bandeja del sistema
     */
    public void setSystemTrayManager(SystemTrayManager systemTrayManager) {
        this.systemTrayManager = systemTrayManager;

        this.systemTrayManager.setOnWindowShowCallback(() -> {
            if (chatController != null) {
                chatController.reconnectWebSocket();
            }
        });

        this.systemTrayManager.setOnLogoutCallback(() -> {
            if (chatController != null) {
                chatController.disconnectWebSocket();
            }
        });
    }

    /**
     * Oculta la ventana y activa el modo System Tray.
     * Programa el cierre del WebSocket. SSE se mantiene activa desde el inicio.
     */
    public void hideWindow() {
        if (!isInChatView || chatController == null) {
            Platform.exit();
            return;
        }

        System.out.println("[ViewManager] Ocultando ventana - iniciando modo bandeja");
        stage.hide();

        if (systemTrayManager != null) {
            systemTrayManager.onWindowHidden(null, null);
        }
    }

    /**
     * Muestra la ventana y desactiva el modo System Tray.
     * Reconecta el WebSocket. SSE se mantiene conectada.
     */
    public void showWindow() {
        System.out.println("[ViewManager] Mostrando ventana desde bandeja");
        stage.show();
        stage.toFront();

        if (systemTrayManager != null) {
            systemTrayManager.onWindowShown();
        }
    }

    /**
     * Desconecta todos los servicios y cierra la aplicación completamente.
     * Usado cuando el usuario cierra sesión o presiona "Salir" desde el tray.
     */
    public void disconnectAllServicesAndExit() {
        System.out.println("[ViewManager] Cerrando aplicación - desconectando todos los servicios...");

        if (webSocketService != null) {
            System.out.println("[ViewManager] Desconectando servicio de chat...");
            webSocketService.disconnect();
        }

        if (systemTrayManager != null) {
            systemTrayManager.shutdown();
        }

        System.out.println("[ViewManager] Todos los servicios desconectados");
        Platform.exit();
    }
    
    /**
     * Registra el servicio de notificaciones para desconexión automática al cerrar.
     * @param notificationService Servicio de notificaciones STOMP
     * @deprecated El servicio STOMP fue comentado. Ahora las notificaciones son vía SSE.
     */
    // public void setNotificationService(NotificationService notificationService) {
    //     this.notificationService = notificationService;
    // }

    // TODO: MEDIA - Reactivar cuando se implemente funcionalidad de envío de imágenes
    // /**
    //  * Registra el servicio de media WebSocket para desconexión automática al cerrar.
    //  * @param mediaWebSocketService Servicio de media WebSocket
    //  */
    // public void setMediaWebSocketService(WebSocketService mediaWebSocketService) {
    //     this.mediaWebSocketService = mediaWebSocketService;
    // }

    /**
     * Muestra la vista de login cuando el usuario cierra sesión.
     * Limpia el estado de la vista de chat y el System Tray.
     */
    public void showLoginView() {
        try {
            if (systemTrayManager != null) {
                systemTrayManager.shutdown();
                systemTrayManager = null;
            }

            isInChatView = false;
            chatController = null;

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());

            LoginController controller = loader.getController();
            controller.setViewManager(this);
            if (authService == null) {
                authService = new AuthService(httpService);
            }
            controller.setAuthService(authService);

            stage.setScene(scene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Muestra la vista de chat y configura los servicios necesarios.
     * @param username Nombre de usuario autenticado
     * @param userId ID único del usuario
     * @param token Token de autenticación
     */
    public void showChatView(String username, String userId, String token) {
        try {
            DatabaseManager dbManager = DatabaseManager.getInstance();
            dbManager.initializeForUser(userId);

            if (webSocketService == null) {
                webSocketService = new WebSocketServiceImpl();
            }

            if (contactService == null) {
                contactService = new ContactService();
            }
            if (messageService == null) {
                messageService = new MessageService(webSocketService, contactService);
            }

            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/chat.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());

            chatController = loader.getController();
            chatController.setWebSocketService(webSocketService);
            chatController.setMessageService(messageService);
            chatController.setContactService(contactService);
            chatController.setViewManager(this);
            chatController.initialize(username, userId, token);

            stage.setScene(scene);
            isInChatView = true;

            if (systemTrayManager == null) {
                systemTrayManager = new SystemTrayManager(stage);
                systemTrayManager.setOnWindowShowCallback(() -> {
                    if (chatController != null) {
                        chatController.reconnectWebSocket();
                    }
                });
                systemTrayManager.setOnLogoutCallback(() -> {
                    if (chatController != null) {
                        chatController.disconnectWebSocket();
                    }
                });
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Muestra la vista de registro de usuario.
     */
    public void showRegisterView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/register.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());

            RegisterController controller = loader.getController();
            controller.setViewManager(this);
            controller.setHttpService(httpService);

            stage.setScene(scene);
            isInChatView = false;
            chatController = null;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Establece el controlador de chat manualmente.
     * Usado por ChatController para registrarse con ViewManager.
     * @param controller Instancia del ChatController
     */
    public void setChatController(ChatController controller) {
        this.chatController = controller;
    }

}
