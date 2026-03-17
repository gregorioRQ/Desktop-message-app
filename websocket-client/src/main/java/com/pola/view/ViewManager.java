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
import com.pola.service.NotificationService;
import com.pola.service.WebSocketService;
import com.pola.service.WebSocketServiceImpl;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Gestor de vistas de la aplicación
 * Principio SOLID: Single Responsibility - Solo maneja la navegación entre vistas
 */
public class ViewManager {
    private final Stage stage;
    private WebSocketService webSocketService;
    private MessageService messageService;
    private final HttpService httpService;
    private AuthService authService;
    private ContactService contactService;
    private NotificationService notificationService;
    // TODO: MEDIA - Reactivar cuando se implemente funcionalidad de envío de imágenes
    // private WebSocketService mediaWebSocketService;
    
    public ViewManager(Stage stage) {
        this.stage = stage;
        this.httpService = new HttpServiceImpl();
        configureStage();
    }
    
    private void configureStage() {
        stage.setTitle("WebSocket Client");
        stage.setWidth(800);
        stage.setHeight(600);
        stage.setOnCloseRequest(event -> {
            disconnectAllServices();
        });
    }

    /**
     * Desconecta todos los servicios WebSocket cuando la aplicación se cierra.
     * Esto asegura que el servidor de notificaciones sea notificado cuando el cliente se cierre.
     */
    private void disconnectAllServices() {
        System.out.println("[ViewManager] Cerrando aplicación - desconectando todos los servicios...");
        
        // Desconectar servicio de chat (WebSocket binario)
        if (webSocketService != null) {
            System.out.println("[ViewManager] Desconectando servicio de chat...");
            webSocketService.disconnect();
        }
        
        // Desconectar servicio de notificaciones (STOMP)
        if (notificationService != null) {
            System.out.println("[ViewManager] Desconectando servicio de notificaciones...");
            notificationService.disconnect();
        }

        // TODO: MEDIA - Reactivar cuando se implemente funcionalidad de envío de imágenes
        // // Desconectar servicio de media (WebSocket binario)
        // if (mediaWebSocketService != null) {
        //     System.out.println("[ViewManager] Desconectando servicio de media...");
        //     mediaWebSocketService.disconnect();
        // }
        
        System.out.println("[ViewManager] Todos los servicios desconectados");
    }
    
    /**
     * Registra el servicio de notificaciones para desconexión automática al cerrar.
     * @param notificationService Servicio de notificaciones STOMP
     */
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // TODO: MEDIA - Reactivar cuando se implemente funcionalidad de envío de imágenes
    // /**
    //  * Registra el servicio de media WebSocket para desconexión automática al cerrar.
    //  * @param mediaWebSocketService Servicio de media WebSocket
    //  */
    // public void setMediaWebSocketService(WebSocketService mediaWebSocketService) {
    //     this.mediaWebSocketService = mediaWebSocketService;
    // }

    public void showLoginView() {
        try {
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

    public void showChatView(String username, String userId, String token) {
        try {
            // Inicializar la base de datos para este usuario
            DatabaseManager dbManager = DatabaseManager.getInstance();
            dbManager.initializeForUser(userId);
            
            // Crear los servicios ahora que la BD está inicializada
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
            
            ChatController controller = loader.getController();
            controller.setWebSocketService(webSocketService);
            controller.setMessageService(messageService);
            controller.setContactService(contactService);
            controller.setViewManager(this);
            controller.initialize(username, userId, token);
            
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
