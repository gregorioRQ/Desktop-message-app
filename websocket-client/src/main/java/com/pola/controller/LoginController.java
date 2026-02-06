package com.pola.controller;

import com.pola.service.AuthService;
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
    private AuthService authService;
    
    public void setViewManager(ViewManager viewManager) {
        this.viewManager = viewManager;
    }

    public void setAuthService(AuthService authService){
        this.authService = authService;
        attemptAutoLogin();
    }
    
    @FXML
    private void initialize() {
        // Configurar acciones de los botones
        loginButton.setOnAction(event -> handleLogin());
        
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

    private void attemptAutoLogin() {
        if (authService == null) return;
        
        setButtonsEnabled(false);
        statusLabel.setText("Intentando inicio de sesión automático...");
        
        authService.tryAutoLogin()
            .thenAccept(session -> Platform.runLater(() -> {
                viewManager.showChatView(session.getUsername(), session.getUserId(), session.getAccessToken());
            }))
            .exceptionally(e -> {
                Platform.runLater(() -> {
                    setButtonsEnabled(true);
                    statusLabel.setText(""); // Limpiar mensaje si falla auto-login
                });
                return null;
            });
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

        authService.login(username, password)
                    .thenAccept(session -> {
                    Platform.runLater(() -> {
                        showSuccess("Login exitoso");
                        viewManager.showChatView(session.getUsername(), session.getUserId(), session.getAccessToken());
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
