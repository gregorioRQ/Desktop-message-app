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
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
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
    private ContactActionHelper contactActionHelper;
    private MessageActionHelper messageActionHelper;
    private ConnectionActionHelper connectionActionHelper;
    
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
        
        this.contactActionHelper = new ContactActionHelper(contactService, this);
        this.messageActionHelper = new MessageActionHelper(messageService, webSocketService, contactService, this);
        this.connectionActionHelper = new ConnectionActionHelper(webSocketService, contactService, this);

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
        sendButton.setOnAction(event -> messageActionHelper.handleSendMessage());
        connectButton.setOnAction(event -> connectionActionHelper.handleConnect());
        disconnectButton.setOnAction(event -> connectionActionHelper.handleDisconnect());
        addContactButton.setOnAction(e -> contactActionHelper.handleAddContact());
        if (clearChatButton != null) clearChatButton.setOnAction(e -> messageActionHelper.handleClearChat());
        if (blockButton != null) blockButton.setOnAction(e -> contactActionHelper.confirmBlockContact(selectedContact));

        setupMessageListView();
        
        // Enter envía mensaje
        messageInput.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER") && !event.isShiftDown()) {
                event.consume();
                messageActionHelper.handleSendMessage();
            }
        });
        
        // Deshabilitar envío si no está conectado
        sendButton.setDisable(true);
        messageInput.setDisable(true);

        // Configurar celdas para contactos (Botón Bloquear)
        contactsListView.setCellFactory(param -> new ContactListCell(false, contactActionHelper::confirmBlockContact, contactActionHelper::confirmUnblockContact, contactActionHelper::confirmAddContact) {
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

                        // Menú desplegable de opciones (Eliminar)
                        Node optionsNode = null;
                        for (Node n : hbox.getChildren()) {
                            if ("optionsMenu".equals(n.getId())) {
                                optionsNode = n;
                                break;
                            }
                        }

                        if (optionsNode == null) {
                            MenuButton optionsMenu = new MenuButton("⋮");
                            optionsMenu.setId("optionsMenu");
                            optionsMenu.setStyle("-fx-background-color: transparent; -fx-font-weight: bold; -fx-padding: 0 5 0 5; -fx-cursor: hand;");
                            
                            MenuItem deleteItem = new MenuItem("Eliminar");
                            deleteItem.setOnAction(e -> contactActionHelper.confirmDeleteContact(contact));
                            
                            optionsMenu.getItems().add(deleteItem);
                            hbox.getChildren().add(optionsMenu);
                        } else {
                            // Actualizar la acción para asegurar que capture el contacto correcto
                            MenuButton optionsMenu = (MenuButton) optionsNode;
                            optionsMenu.getItems().clear();
                            MenuItem deleteItem = new MenuItem("Eliminar");
                            deleteItem.setOnAction(e -> contactActionHelper.confirmDeleteContact(contact));
                            optionsMenu.getItems().add(deleteItem);
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
        blockedContactsListView.setCellFactory(param -> new ContactListCell(true, contactActionHelper::confirmBlockContact, contactActionHelper::confirmUnblockContact, contactActionHelper::confirmAddContact));
    }

    private void setupMessageListView(){
        messageListView.setCellFactory(lv -> new MessageListCell(currentUsername, messageActionHelper::handleDeleteMessage, messageActionHelper::handleEditMessage));
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
    
    public void updateConnectionStatus(boolean connected) {
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

    public void refreshContactsList() {
        contactsListView.refresh();
    }

    public void resetChatViewIfSelected(Contact contact) {
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

    // --- Getters y Setters para los Helpers ---

    public Contact getSelectedContact() {
        return selectedContact;
    }

    public TextArea getMessageInput() {
        return messageInput;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public String getAuthToken() {
        return authToken;
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void showStatus(String message, Color color) {
        statusLabel.setText(message);
        if (color != null) {
            // Convertir Color de JavaFX a string CSS simple (aproximado)
            String colorString = "#" + color.toString().substring(2, 8);
            statusLabel.setStyle("-fx-text-fill: " + colorString + ";");
        }
    }
}
