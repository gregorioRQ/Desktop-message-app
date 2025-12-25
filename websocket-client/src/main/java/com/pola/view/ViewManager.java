package com.pola.view;

import java.io.IOException;

import com.pola.controller.ChatController;
import com.pola.controller.LoginController;
import com.pola.controller.RegisterController;
import com.pola.database.DatabaseManager;
import com.pola.service.ContactService;
import com.pola.service.HttpService;
import com.pola.service.HttpServiceImpl;
import com.pola.service.MessageService;
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
    private ContactService contactService;
    
    public ViewManager(Stage stage) {
        this.stage = stage;
        this.httpService = new HttpServiceImpl();
        // Los demás servicios se crearán cuando se necesiten (lazy initialization)
        configureStage();
    }
    
    private void configureStage() {
        stage.setTitle("WebSocket Client");
        stage.setWidth(800);
        stage.setHeight(600);
        stage.setOnCloseRequest(event -> {
            webSocketService.disconnect();
        });
    }

    public void showLoginView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/login.fxml"));
            Scene scene = new Scene(loader.load());
            scene.getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());
            
            LoginController controller = loader.getController();
            controller.setViewManager(this);
            controller.setHttpService(httpService);
            
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
            if (messageService == null) {
                messageService = new MessageService(webSocketService);
            }
            if (contactService == null) {
                contactService = new ContactService();
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
