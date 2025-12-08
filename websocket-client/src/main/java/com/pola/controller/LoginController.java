package com.pola.controller;

import com.pola.proto.LoginProto.LoginRequest;
import com.pola.proto.LoginProto.LoginResponse;
import com.pola.service.HttpService;
import com.pola.view.ViewManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Controlador para la vista de login
 * Principio SOLID: Single Responsibility - Solo maneja la lógica de la vista de login
 */
public class LoginController {
     @FXML
    private TextField usernameField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private Button loginButton;
    
    @FXML
    private Button skipButton;
    
    @FXML
    private Label statusLabel;

    @FXML
    private Label registerLink;
    
    private ViewManager viewManager;
    private HttpService httpService;
    
    public void setViewManager(ViewManager viewManager) {
        this.viewManager = viewManager;
    }

    public void setHttpService(HttpService httpService){
        this.httpService = httpService;
    }
    
    @FXML
    private void initialize() {
        // Configurar acciones de los botones
        loginButton.setOnAction(event -> handleLogin());
        skipButton.setOnAction(event -> handleSkip());
        
        // Enter en username va a password
        usernameField.setOnAction(event -> passwordField.requestFocus());
        
        // Enter en password hace login
        passwordField.setOnAction(event -> handleLogin());

        // Efecto hover en el link de registro
        registerLink.setOnMouseEntered(event -> 
            registerLink.setStyle("-fx-text-fill: #ffffff; -fx-underline: true; -fx-cursor: hand;"));
        registerLink.setOnMouseExited(event -> 
            registerLink.setStyle("-fx-text-fill: #e0e0e0; -fx-underline: true; -fx-cursor: hand;"));
    }

    @FXML
    private void handleRegisterLink(){
        viewManager.showRegisterView();
    }
    
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty()) {
            statusLabel.setText("Por favor ingrese un nombre de usuario");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }

         if (password.isEmpty()) {
            showError("Por favor ingrese una contraseña");
            return;
        }

        // Deshabilitar botones mientras se procesa
            setButtonsEnabled(false);
            showInfo("Iniciando sesión...");

        LoginRequest loginRequest = LoginRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .build();
        
        httpService.login(loginRequest, LoginResponse.class)
                    .thenAccept(response -> {
                    Platform.runLater(() -> {
                        if (response.getSuccess()) {
                            showSuccess("Login exitoso");
                            viewManager.showChatView(username);
                        } else {
                            showError("Error: " + response.getMessage());
                            setButtonsEnabled(true);
                        }
                    });
                })
                .exceptionally(error -> {
                    Platform.runLater(() -> {
                        showError("Error de conexión: " + error.getMessage());
                        setButtonsEnabled(true);
                    });
                    return null;
                });
      
    }
    
    private void handleSkip() {
        // Ir directamente al chat con usuario "Guest"
        viewManager.showChatView("Guest");
    }

    private void setButtonsEnabled(boolean enabled) {
        loginButton.setDisable(!enabled);
        skipButton.setDisable(!enabled);
        usernameField.setDisable(!enabled);
        passwordField.setDisable(!enabled);
    }

    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: red;");
    }
    
    private void showInfo(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: blue;");
    }
    
    private void showSuccess(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: green;");
    }
}
