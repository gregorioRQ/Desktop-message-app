package com.pola.controller;

import com.pola.model.Message;
import com.pola.service.MessageService;
import com.pola.service.WebSocketService;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

/**
 * Controlador para la vista de chat
 * Principio SOLID: Single Responsibility - Solo maneja la lógica de la vista de chat
 */
public class ChatController {
    @FXML
    private ListView<String> messageListView;
    
    @FXML
    private TextArea messageInput;
    
    @FXML
    private Button sendButton;
    
    @FXML
    private Button connectButton;
    
    @FXML
    private Button disconnectButton;
    
    @FXML
    private Label statusLabel;
    
    @FXML
    private Label usernameLabel;
    
    @FXML
    private VBox contactsPanel;
    
    private WebSocketService webSocketService;
    private MessageService messageService;
    private String currentUsername;
    
    public void initialize(String username, WebSocketService webSocketService, 
                          MessageService messageService) {
        this.currentUsername = username;
        this.webSocketService = webSocketService;
        this.messageService = messageService;
        
        setupUI();
        setupListeners();
        setupWebSocketListeners();
    }
    
    private void setupUI() {
        usernameLabel.setText("Usuario: " + currentUsername);
        updateConnectionStatus(false);
        
        // Configurar botones
        sendButton.setOnAction(event -> handleSendMessage());
        connectButton.setOnAction(event -> handleConnect());
        disconnectButton.setOnAction(event -> handleDisconnect());
        
        // Enter envía mensaje
        messageInput.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER") && !event.isShiftDown()) {
                event.consume();
                handleSendMessage();
            }
        });
        
        // Deshabilitar envío si no está conectado
        sendButton.setDisable(true);
        messageInput.setDisable(true);
    }
    
    private void setupListeners() {
        // Vincular lista de mensajes con el servicio
        messageService.getMessages().addListener(
            (javafx.collections.ListChangeListener.Change<? extends Message> change) -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        for (Message msg : change.getAddedSubList()) {
                            Platform.runLater(() -> 
                                messageListView.getItems().add(msg.getDisplayText())
                            );
                        }
                    }
                }
            }
        );
    }
    
    private void setupWebSocketListeners() {
        // Listener de mensajes
        /*webSocketService.setMessageListener(wsMessage -> {
            messageService.processReceivedMessage(wsMessage);
        });*/
        
        // Listener de conexión
        webSocketService.setConnectionListener(connected -> {
            Platform.runLater(() -> updateConnectionStatus(connected));
        });
        
        // Listener de errores
        webSocketService.setErrorListener(error -> {
            Platform.runLater(() -> {
                statusLabel.setText("Error: " + error.getMessage());
                statusLabel.setStyle("-fx-text-fill: red;");
            });
        });
    }
    
    private void handleConnect() {
        statusLabel.setText("Conectando...");
        statusLabel.setStyle("-fx-text-fill: blue;");
        
        new Thread(() -> {
            try {
                webSocketService.connect();
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error al conectar: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        }).start();
    }
    
    private void handleDisconnect() {
        webSocketService.disconnect();
    }
    
    private void handleSendMessage() {
        String content = messageInput.getText().trim();
        
        if (content.isEmpty()) {
            return;
        }
        
        if (!webSocketService.isConnected()) {
            statusLabel.setText("No conectado al servidor");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        
        try {
            messageService.sendTextMessage(content, currentUsername, "all");
            messageInput.clear();
        } catch (Exception e) {
            statusLabel.setText("Error al enviar: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }
    
    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            statusLabel.setText("Conectado");
            statusLabel.setStyle("-fx-text-fill: green;");
            connectButton.setDisable(true);
            disconnectButton.setDisable(false);
            sendButton.setDisable(false);
            messageInput.setDisable(false);
        } else {
            statusLabel.setText("Desconectado");
            statusLabel.setStyle("-fx-text-fill: red;");
            connectButton.setDisable(false);
            disconnectButton.setDisable(true);
            sendButton.setDisable(true);
            messageInput.setDisable(true);
        }
    }
}
