package com.pola.controller;

import com.pola.proto.RegisterProto.RegisterRequest;
import com.pola.proto.RegisterProto.RegisterResponse;
import com.pola.service.HttpService;
import com.pola.view.ViewManager;
import com.pola.service.NotificationService;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class RegisterController {
    @FXML
    private TextField usernameField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private PasswordField confirmPasswordField;
    
    @FXML
    private Button registerButton;
    
    @FXML
    private Button backButton;
    
    @FXML
    private Label statusLabel;
    
    @FXML
    private VBox messageContainer;
    
    @FXML
    private Label messageLabel;
    
    private ViewManager viewManager;
    private HttpService httpService;
    
    public void setViewManager(ViewManager viewManager) {
        this.viewManager = viewManager;
    }
    
    public void setHttpService(HttpService httpService) {
        this.httpService = httpService;
    }

    @FXML
    private void initialize() {
        // Configurar acciones de los botones
        registerButton.setOnAction(event -> handleRegister());
        backButton.setOnAction(event -> handleBack());
        
        // Enter en username va a password
        usernameField.setOnAction(event -> passwordField.requestFocus());
        
        // Enter en password va a confirm password
        passwordField.setOnAction(event -> confirmPasswordField.requestFocus());
        
        // Enter en confirm password hace el registro
        confirmPasswordField.setOnAction(event -> handleRegister());
    }

    private void handleRegister(){
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        
        // Validaciones locales
        if (username.isEmpty()) {
            showError("Por favor ingrese un nombre de usuario");
            return;
        }
        
        if (username.length() < 3) {
            showError("El nombre de usuario debe tener al menos 3 caracteres");
            return;
        }
        
        if (password.isEmpty()) {
            showError("Por favor ingrese una contraseña");
            return;
        }
        
        if (password.length() < 6) {
            showError("La contraseña debe tener al menos 6 caracteres");
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            showError("Las contraseñas no coinciden");
            return;
        }
        
        // Deshabilitar botones mientras se procesa
        setButtonsEnabled(false);
        showInfo("Registrando usuario...");
        hideServerMessage();

       
        RegisterRequest request = RegisterRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .build();
        
        httpService.createProfile(request, RegisterResponse.class)
                .thenAccept(response -> {
                    Platform.runLater(() -> {
                        if (response.getSuccess()) {
                            // Enviar notificación de usuario creado al servicio de notificaciones
                            new Thread(() -> {
                                try {
                                    String newUserId = response.getUserId();
                                    NotificationService ns = new NotificationService(newUserId, username);
                                    ns.setOnStompConnected(() -> {
                                        ns.sendUserCreateNotification(newUserId);
                                        try { Thread.sleep(500); } catch (InterruptedException e) {}
                                        ns.disconnect();
                                    });
                                    ns.connect();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }).start();

                            showSuccess("¡Cuenta creada exitosamente!");
                            // Esperar 1.5 segundos y volver al login
                            new Thread(() -> {
                                try {
                                    Thread.sleep(1500);
                                    Platform.runLater(() -> viewManager.showLoginView());
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            }).start();
                        } else {
                            // Mostrar mensaje de error del servidor
                            showServerError(response.getMessage());
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

    private void handleBack() {
        viewManager.showLoginView();
    }
    
    private void setButtonsEnabled(boolean enabled) {
        registerButton.setDisable(!enabled);
        backButton.setDisable(!enabled);
        usernameField.setDisable(!enabled);
        passwordField.setDisable(!enabled);
        confirmPasswordField.setDisable(!enabled);
    }

    /**
     * Muestra un error de validación local
     */
    private void showError(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: red;");
        hideServerMessage();
    }
    
    /**
     * Muestra información de proceso
     */
    private void showInfo(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: blue;");
        hideServerMessage();
    }
    
    /**
     * Muestra mensaje de éxito
     */
    private void showSuccess(String message) {
        statusLabel.setText(message);
        statusLabel.setStyle("-fx-text-fill: green;");
        hideServerMessage();
    }

     /**
     * Muestra un mensaje de error del servidor en un área destacada
     * Este método es útil para errores que vienen del backend
     */
    private void showServerError(String message) {
        messageLabel.setText("ADVERTENCIA: " + message);
        messageLabel.setStyle("-fx-text-fill: white; -fx-background-color: #f44336; " +
                             "-fx-padding: 10; -fx-background-radius: 5;");
        messageContainer.setVisible(true);
        messageContainer.setManaged(true);
        statusLabel.setText("");
    }
    
    /**
     * Oculta el mensaje del servidor
     */
    private void hideServerMessage() {
        messageContainer.setVisible(false);
        messageContainer.setManaged(false);
    }

}
