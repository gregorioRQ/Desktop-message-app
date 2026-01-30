package com.pola.controller;

import com.pola.database.DatabaseManager;
import com.pola.model.ChatMessage;
import com.pola.model.Contact;
import com.pola.model.Message;
import com.pola.model.Notification;
import com.pola.proto.MessagesProto.AuthMessage;
import com.pola.proto.MessagesProto.WsMessage;
import com.pola.service.ContactService;
import com.pola.service.MessageService;
import com.pola.service.NotificationService;
import com.pola.service.WebSocketService;
import com.pola.view.MessageListCell;
import com.pola.view.ContactListCell;
import com.pola.view.ChatDialogs;
import com.pola.view.ViewManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Circle;

/**
 * Controlador para la vista de chat
 * Principio SOLID: Single Responsibility - Solo maneja la lógica de la vista de chat
 */
public class ChatController {
    @FXML
    private ListView<ChatMessage> messageListView;

    @FXML
    private ListView<Contact> contactsListView;

    @FXML
    private ListView<Contact> blockedContactsListView;

    @FXML
    private ListView<Notification> notificationsListView;

    @FXML
    private TextField searchContactField;
    
    @FXML
    private TextArea messageInput;
    
    @FXML
    private Button sendButton;
    
    @FXML
    private Button connectButton;
    
    @FXML
    private Button disconnectButton;

    @FXML
    private Button addContactButton;
    
    @FXML
    private Button clearChatButton;

    @FXML
    private Button blockButton;
    
    @FXML
    private Label statusLabel;
    
    @FXML
    private Label usernameLabel;

    @FXML
    private Label chatTitleLabel;

    @FXML
    private Label noContactsLabel;
    
    @FXML
    private VBox contactsPanel;

    @FXML
    private Circle notificationBadge;

    @FXML
    private Button clearNotificationsButton;

    private WebSocketService webSocketService;
    private MessageService messageService;
    private ContactService contactService;
    private NotificationService notificationService;
    private ViewManager viewManager;
    private String currentUsername;
    private String currentUserId;
    private String authToken;
    private Contact selectedContact;
    private NotificationUIController notificationUIController;
    
    public void initialize(String username, String userId, String token) {
        this.currentUsername = username;
        this.currentUserId = userId;
        this.authToken = token;

        DatabaseManager.getInstance().initializeForUser(userId);

        messageService.setCurrentUserId(userId);
        messageService.setCurrentUsername(username);
        contactService.setCurrentUserId(userId);
        contactService.setCurrentUsername(username);
        contactService.setWebSocketService(webSocketService);
        
        setupUI();
        setupListeners();
        setupWebSocketListeners();
        loadContacts();
        
        // Delegar la visualización de notificaciones al nuevo componente
        this.notificationUIController = new NotificationUIController(
            notificationsListView, notificationBadge, clearNotificationsButton, messageService.getNotifications());
    }
    
    public void setWebSocketService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }
    
    public void setMessageService(MessageService messageService) {
        this.messageService = messageService;
    }
    
    public void setContactService(ContactService contactService) {
        this.contactService = contactService;
    }
    
    public void setViewManager(ViewManager viewManager) {
        this.viewManager = viewManager;
    }
    
    private void setupUI() {
        usernameLabel.setText("Usuario: " + currentUsername);
        updateConnectionStatus(false);
        
        // Configurar botones
        sendButton.setOnAction(event -> handleSendMessage());
        connectButton.setOnAction(event -> handleConnect());
        disconnectButton.setOnAction(event -> handleDisconnect());
        addContactButton.setOnAction(e -> handleAddContact());
        if (clearChatButton != null) clearChatButton.setOnAction(e -> handleClearChat());
        if (blockButton != null) blockButton.setOnAction(e -> handleBlockContact());

        setupMessageListView();
        
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

        // Configurar celdas para contactos (Botón Bloquear)
        contactsListView.setCellFactory(param -> new ContactListCell(false, this::confirmBlockContact, this::confirmUnblockContact, this::confirmAddContact) {
            @Override
            protected void updateItem(Contact contact, boolean empty) {
                super.updateItem(contact, empty);
                if (contact != null && !empty) {
                    // Añadir indicador de estado
                    if (getGraphic() instanceof HBox) {
                        HBox hbox = (HBox) getGraphic();
                        Node indicator = null;
                        for (Node n : hbox.getChildren()) {
                            if ("statusIndicator".equals(n.getId())) {
                                indicator = n;
                                break;
                            }
                        }
                        if (indicator == null) {
                            Label statusLabel = new Label();
                            statusLabel.setId("statusIndicator");
                            statusLabel.setStyle("-fx-font-size: 10px; -fx-padding: 0 5 0 0;");
                            hbox.getChildren().add(0, statusLabel);
                            indicator = statusLabel;
                        }
                        
                        boolean isOnline = contact.getContactUserId() != null && contactService.isContactOnline(contact.getContactUserId());
                        ((Label) indicator).setText(isOnline ? "Conectado" : "Desconectado");
                        ((Label) indicator).setTextFill(isOnline ? Color.GREEN : Color.GRAY);

                        // Ocultar icono de handshake si el contacto ya está confirmado
                        // Se asume que el botón de handshake tiene el ID "handshakeButton" en ContactListCell
                        for (Node n : hbox.getChildren()) {
                            if ("handshakeButton".equals(n.getId())) {
                                boolean showHandshake = !contact.isConfirmed();
                                n.setVisible(showHandshake);
                                n.setManaged(showHandshake);
                            }
                        }
                    }
                }
            }
        });

        // Configurar panel de contactos bloqueados si no existe en FXML
        if (blockedContactsListView == null) {
            blockedContactsListView = new ListView<>();
            Label blockedLabel = new Label("Contactos Bloqueados");
            blockedLabel.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 5 0;");
            contactsPanel.getChildren().addAll(blockedLabel, blockedContactsListView);
        }
        
        // Configurar celdas para contactos bloqueados (Botón Desbloquear)
        blockedContactsListView.setCellFactory(param -> new ContactListCell(true, this::confirmBlockContact, this::confirmUnblockContact, this::confirmAddContact));
    }

    private void setupMessageListView(){
        messageListView.setCellFactory(lv -> new MessageListCell(currentUsername, this::handleDeleteMessage, this::handleEditMessage));
    }
    
    private void setupListeners() {
        //listener de seleccion de contacto
        contactsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldContact, newContact) -> {
            if(newContact != null){
                handleContactSelected(newContact);
            }
        });

        // Vincular lista de mensajes con el servicio
        messageService.getMessages().addListener(
            (javafx.collections.ListChangeListener.Change<? extends com.pola.model.ChatMessage> change) -> {
                while (change.next()) {
                    if (change.wasAdded()) {
                        Platform.runLater(() -> {
                        // La lista se actualiza automáticamente con el binding
                        // Solo hacer auto-scroll
                        if (!messageListView.getItems().isEmpty()) {
                            messageListView.scrollTo(messageListView.getItems().size() - 1);
                        }
                    });
                    if (change.wasRemoved()) {
                    // Mensaje eliminado, la lista se actualiza automáticamente
                    Platform.runLater(() -> {
                        System.out.println("Mensaje removido de la vista");
                    });
                }
            }
            }
                
            }
        );

        // busqueda de contactos filtro simple
        searchContactField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.trim().isEmpty()) {
                contactsListView.setItems(contactService.getContacts());
            } else {
                contactsListView.setItems(
                    contactService.getContacts().filtered(contact ->
                        contact.getContactUsername().toLowerCase()
                            .contains(newVal.toLowerCase())
                    )
                );
            }
        });

        // vincular la ObservableList del servicio con el listview
        messageListView.setItems(messageService.getMessages());

        // Listener de errores del servidor (ej. usuario bloqueado)
        messageService.setErrorListener(errorMessage -> {
            ChatDialogs.showError("Error del Servidor", errorMessage);
        });

        // Listener para cuando se detecta que un usuario nos bloqueó
        contactService.setOnBlockedByListener(username -> {
            Platform.runLater(() -> {
                if (selectedContact != null && selectedContact.getContactUsername().equals(username)) {
                    updateChatInputState(true); // true = estamos bloqueados
                }
            });
        });

        // Listener para cuando se detecta que un usuario nos desbloqueó
        contactService.setOnUnblockedByListener(username -> {
            Platform.runLater(() -> {
                if (selectedContact != null && selectedContact.getContactUsername().equals(username)) {
                    updateChatInputState(false); // false = NO estamos bloqueados (habilitar chat)
                }
            });
        });

        // Listener para refrescar la lista cuando cambia el estado online/offline
        contactService.setOnOnlineStatusChanged(() -> {
            Platform.runLater(() -> contactsListView.refresh());
        });
    }
    
    private void setupWebSocketListeners() {
        // Listener de mensajes
        webSocketService.setMessageListener(wsMessage -> {
            messageService.processReceivedMessage(wsMessage);
        });
        
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

        // Listener de autenticación exitosa
        webSocketService.setAuthSuccessListener(userId -> {
            if (notificationService != null) {
                notificationService.sendUserOnlineNotification(userId);
            }
        });
    }

    private void loadContacts() {
        // Cargar contactos desde la base de datos
        contactService.loadContacts(currentUserId);
        
        // Vincular lista de contactos con el ListView
        contactsListView.setItems(contactService.getContacts());

        // Vincular lista de contactos bloqueados
        blockedContactsListView.setItems(contactService.getBlockedContacts());
        
        // Mostrar/ocultar mensaje de "no hay contactos"
        contactService.getContacts().addListener(
            (javafx.collections.ListChangeListener.Change<?> change) -> {
                boolean hasContacts = !contactService.getContacts().isEmpty();
                noContactsLabel.setVisible(!hasContacts);
                noContactsLabel.setManaged(!hasContacts);
            }
        );
        
        // Verificar si hay contactos
        boolean hasContacts = !contactService.getContacts().isEmpty();
        noContactsLabel.setVisible(!hasContacts);
        noContactsLabel.setManaged(!hasContacts);
    }

    private void handleContactSelected(Contact contact) {
        selectedContact = contact;
        chatTitleLabel.setText("Chat con: " + contact.getContactUsername());
        
        // Limpiar mensajes anteriores
        messageListView.getItems().clear();
        
        // Cargar historial del contacto
        messageService.loadChatHistory(contact);
        
        // Verificar estado de bloqueo y conexión para habilitar UI
        updateChatInputState(contactService.isUserBlockingMe(contact.getContactUsername()));

        if (!contactService.isUserBlockingMe(contact.getContactUsername()) && webSocketService.isConnected()) {
            messageInput.requestFocus();
        }
        if (blockButton != null) {
            blockButton.setDisable(false);
        }
    }
    
    private void handleConnect() {
        statusLabel.setText("Conectando...");
        statusLabel.setStyle("-fx-text-fill: blue;");
        
        new Thread(() -> {
            try {
                webSocketService.connect();
                
                // Conectar servicio de notificaciones (en el mismo hilo secundario)
                if (notificationService == null) {
                    notificationService = new NotificationService(currentUserId, currentUsername);
                    notificationService.addNotificationListener(mensaje -> {
                        Platform.runLater(() -> {
                            System.out.println("Notificación recibida: " + mensaje);
                        });
                    });
                }

                notificationService.setPresenceListener((userId, isOnline) -> {
                    Platform.runLater(() -> {
                        contactService.setContactOnline(userId, isOnline);
                        
                        // Si el contacto se conectó, le notificamos que nosotros también estamos en línea
                        if (isOnline) {
                            contactService.notifyContactWeAreOnline(userId);
                        }
                    });
                });

                contactService.setNotificationService(notificationService);
                notificationService.connect();

                Thread.sleep(200);

                // enviar el mensaje de autenticacion cuando se conecta
               Platform.runLater(()-> 
                sendAuthMessage());
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error al conectar: " + e.getMessage());
                    statusLabel.setStyle("-fx-text-fill: red;");
                });
            }
        }).start();
    }

    private void sendAuthMessage(){
        try{
            if(authToken == null || authToken.isEmpty()){
                statusLabel.setText("Error: Token no disponible");
                statusLabel.setStyle("-fx-text-fill: red");
                return;
            }

            statusLabel.setText("Autenticando...");
            statusLabel.setStyle("-fx-text-fill: blue");

            AuthMessage authMessage = AuthMessage.newBuilder()
                .setToken(authToken)
                .build();
                
            WsMessage wsMessage = WsMessage.newBuilder()
                .setAuthMessage(authMessage)
                .build();

            // envia el token a chat-service
            webSocketService.sendMessage(wsMessage);
            System.out.println("Mensaje de autenticacion enviado");
        }catch(Exception e){
            statusLabel.setText("Error en la autenticación: " + e.getMessage());
        }
    }
    
    private void handleDisconnect() {
        webSocketService.disconnect();
        if (notificationService != null) {
            notificationService.disconnect();
        }
        // Forzar la actualización de la UI a desconectado inmediatamente.
        // Esto garantiza que se limpien los usuarios en línea incluso si el socket ya estaba cerrado.
        updateConnectionStatus(false);
    }
    
    private void handleSendMessage() {
        String content = messageInput.getText().trim();
        
        if (content.isEmpty()) {
            return;
        }

        if(selectedContact == null){
            statusLabel.setText("Selecciona un contacto primero");
            statusLabel.setStyle("-fx-text-fill: orange");
            return;
        }
        
        if (!webSocketService.isConnected()) {
            statusLabel.setText("No conectado al servidor");
            statusLabel.setStyle("-fx-text-fill: red;");
            return;
        }
        
        try {
            messageService.sendTextMessage(content, currentUsername);
            messageInput.clear();
        } catch (Exception e) {
            statusLabel.setText("Error al enviar: " + e.getMessage());
            statusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    private void handleAddContact(){
        ChatDialogs.showAddContactDialog(currentUsername, (username) -> {
            Contact contact = contactService.addContact(currentUserId, username, true);
            return contact != null;
        });
    }

    private void handleClearChat() {
        if (selectedContact == null) return;

        ChatDialogs.showClearChatDialog(
            selectedContact.getContactUsername(),
            () -> messageService.clearChatHistory(selectedContact, false),
            () -> messageService.clearChatHistory(selectedContact, true)
        );
    }

    private void handleBlockContact() {
        if (selectedContact == null) return;
        confirmBlockContact(selectedContact);
    }

    private void handleDeleteMessage(ChatMessage message){
        messageService.deleteOneMessage(message);
    }

    private void handleEditMessage(ChatMessage message){
        ChatDialogs.showEditMessageDialog(message.getContent(), newContent -> {
            messageService.editMessage(message, newContent);
        });
    }


    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            statusLabel.setText("Conectado");
            statusLabel.setStyle("-fx-text-fill: green;");
            connectButton.setDisable(true);
            disconnectButton.setDisable(false);

            // habilitar envio si hay contacto selecicondo
            if(selectedContact != null){
                updateChatInputState(contactService.isUserBlockingMe(selectedContact.getContactUsername()));
            }
        } else {
            statusLabel.setText("Desconectado");
            statusLabel.setStyle("-fx-text-fill: red;");
            connectButton.setDisable(false);
            disconnectButton.setDisable(true);
            sendButton.setDisable(true);
            messageInput.setDisable(true);
            
            if (contactService != null) {
                contactService.clearOnlineUsers();
            }
        }
    }

    /**
     * Actualiza el estado de los inputs del chat basado en si estamos bloqueados o desconectados
     */
    private void updateChatInputState(boolean isBlocked) {
        boolean canSend = webSocketService.isConnected() && !isBlocked;
        
        sendButton.setDisable(!canSend);
        messageInput.setDisable(!canSend);
        
        if (isBlocked) {
            messageInput.setPromptText("No puedes enviar mensajes a este usuario.");
        } else {
            messageInput.setPromptText("");
        }
    }

    private void confirmAddContact(Contact contact) {
        ChatDialogs.showConfirmation(
            "Añadir Contacto", 
            null, 
            "¿Quieres añadir este usuario a tu lista de contactos?", 
            () -> {
                contactService.confirmContact(contact);
                contactsListView.refresh();
            }
        );
    }

    private void confirmBlockContact(Contact contact) {
        ChatDialogs.showConfirmation(
            "Bloquear Contacto", 
            "¿Bloquear a " + contact.getContactUsername() + "?", 
            "No podrás recibir mensajes de este usuario.", 
            () -> {
                contactService.blockContact(contact);
                // Si el contacto bloqueado era el seleccionado, limpiar chat
                if (selectedContact != null && selectedContact.getId() == contact.getId()) {
                    messageListView.getItems().clear();
                    selectedContact = null;
                    chatTitleLabel.setText("");
                    sendButton.setDisable(true);
                    messageInput.setDisable(true);
                    if (blockButton != null) blockButton.setDisable(true);
                    if (clearChatButton != null) clearChatButton.setDisable(true);
                }
            }
        );
    }

    private void confirmUnblockContact(Contact contact) {
        ChatDialogs.showConfirmation(
            "Desbloquear Contacto", 
            "¿Desbloquear a " + contact.getContactUsername() + "?", 
            "Podrás volver a intercambiar mensajes.", 
            () -> contactService.unblockContact(contact)
        );
    }
}
