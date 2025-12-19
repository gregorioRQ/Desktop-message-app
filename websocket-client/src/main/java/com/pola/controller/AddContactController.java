package com.pola.controller;

import com.pola.model.Contact;
import com.pola.service.ContactService;
import com.pola.view.ViewManager;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class AddContactController {
     @FXML
    private TextField usernameField;
    
    @FXML
    private TextField nicknameField;
    
    @FXML
    private Button addButton;
    
    @FXML
    private Button cancelButton;
    
    @FXML
    private Label statusLabel;
    
    private ViewManager viewManager;
    private ContactService contactService;
    private String currentUserId;
    private String currentUsername;
    private String authToken;
    
    public void setViewManager(ViewManager viewManager) {
        this.viewManager = viewManager;
    }
    
    public void setContactService(ContactService contactService) {
        this.contactService = contactService;
    }
    
    public void setUserInfo(String userId, String username, String token) {
        this.currentUserId = userId;
        this.currentUsername = username;
        this.authToken = token;
    }
    
    @FXML
    private void initialize() {
        addButton.setOnAction(event -> handleAddContact());
        cancelButton.setOnAction(event -> handleCancel());
        
        // Enter en username va a nickname
        usernameField.setOnAction(event -> nicknameField.requestFocus());
        
        // Enter en nickname agrega el contacto
        nicknameField.setOnAction(event -> handleAddContact());
    }
    
    private void handleAddContact() {
        String username = usernameField.getText().trim();
        String nickname = nicknameField.getText().trim();
        
        // Validaciones
        if (username.isEmpty()) {
            showError("Por favor ingrese el username del contacto");
            return;
        }
        
        if (username.length() < 3) {
            showError("El username debe tener al menos 3 caracteres");
            return;
        }
        
        // No puede agregarse a sí mismo
        if (username.equalsIgnoreCase(currentUsername)) {
            showError("No puedes agregarte a ti mismo como contacto");
            return;
        }
        
        // Validar caracteres (igual que en registro)
        if (!username.matches("^[a-zA-Z0-9_-]+$")) {
            showError("El username solo puede contener letras, números, guiones y guiones bajos");
            return;
        }
        
        // Deshabilitar botones mientras se procesa
        setButtonsEnabled(false);
        showInfo("Agregando contacto...");
        
        // Agregar contacto
        Contact contact = contactService.addContact(
            currentUserId, 
            username, 
            nickname.isEmpty() ? "No_apodo" : nickname
        );
        
        if (contact != null) {
            showSuccess("Contacto agregado: " + contact.getDisplayName());
            
            // Esperar un momento y volver al chat
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    javafx.application.Platform.runLater(() -> returnToChat());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        } else {
            showError("Error al agregar contacto");
            setButtonsEnabled(true);
        }
    }
    
    private void handleCancel() {
        returnToChat();
    }
    
    private void returnToChat() {
        viewManager.showChatView(currentUsername, currentUserId, authToken);
    }
    
    private void setButtonsEnabled(boolean enabled) {
        addButton.setDisable(!enabled);
        cancelButton.setDisable(!enabled);
        usernameField.setDisable(!enabled);
        nicknameField.setDisable(!enabled);
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
