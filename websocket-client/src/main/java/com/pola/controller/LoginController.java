package com.pola.controller;

import com.pola.view.ViewManager;

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
    
    private ViewManager viewManager;
    
    public void setViewManager(ViewManager viewManager) {
        this.viewManager = viewManager;
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
    }
    
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        
        if (username.isEmpty()) {
            statusLabel.setText("Por favor ingrese un nombre de usuario");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        
        // Por ahora solo validamos que haya username
        // En el futuro se puede agregar autenticación real
        statusLabel.setText("Iniciando sesión...");
        statusLabel.setStyle("-fx-text-fill: blue;");
        
        viewManager.showChatView(username);
    }
    
    private void handleSkip() {
        // Ir directamente al chat con usuario "Guest"
        viewManager.showChatView("Guest");
    }
}
