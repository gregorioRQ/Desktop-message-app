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
import com.pola.view.ViewManager;

import javafx.application.Platform;
import javafx.fxml.FXML;
import java.util.Optional;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Alert;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.shape.Circle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Pos;

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

    private WebSocketService webSocketService;
    private MessageService messageService;
    private ContactService contactService;
    private NotificationService notificationService;
    private ViewManager viewManager;
    private String currentUsername;
    private String currentUserId;
    private String authToken;
    private Contact selectedContact;
    
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
        notificationsListView.setItems(messageService.getNotifications());
        updateNotificationBadge();
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
        contactsListView.setCellFactory(param -> new ContactListCell(false));

        // Configurar panel de contactos bloqueados si no existe en FXML
        if (blockedContactsListView == null) {
            blockedContactsListView = new ListView<>();
            Label blockedLabel = new Label("Contactos Bloqueados");
            blockedLabel.setStyle("-fx-font-weight: bold; -fx-padding: 10 0 5 0;");
            contactsPanel.getChildren().addAll(blockedLabel, blockedContactsListView);
        }
        
        // Configurar celdas para contactos bloqueados (Botón Desbloquear)
        blockedContactsListView.setCellFactory(param -> new ContactListCell(true));
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
            showErrorAlert("Error del Servidor", errorMessage);
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

        // Listener para actualizar el badge de notificaciones
        messageService.getNotifications().addListener((javafx.collections.ListChangeListener.Change<? extends Notification> c) -> {
            Platform.runLater(this::updateNotificationBadge);
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
                    notificationService = new NotificationService(currentUserId);
                    notificationService.addNotificationListener(mensaje -> {
                        Platform.runLater(() -> {
                            System.out.println("Notificación recibida: " + mensaje);
                        });
                    });
                }
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
        // Si NotificationService tiene un método disconnect, deberías llamarlo aquí también
        // if (notificationService != null) {
        //     notificationService.disconnect();
        // }
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
        showAddContactDialog();
    }

    private void handleClearChat() {
        if (selectedContact == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Vaciar Chat");
        alert.setHeaderText("¿Deseas vaciar el chat con " + selectedContact.getContactUsername() + "?");
        alert.setContentText("Selecciona una opción:");

        ButtonType btnMe = new ButtonType("Solo para mí");
        ButtonType btnAll = new ButtonType("Para todos");
        ButtonType btnCancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(btnMe, btnAll, btnCancel);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == btnMe) {
                messageService.clearChatHistory(selectedContact, false);
            } else if (result.get() == btnAll) {
                messageService.clearChatHistory(selectedContact, true);
            }
        }
    }

    private void handleBlockContact() {
        if (selectedContact == null) return;

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Bloquear Contacto");
        alert.setHeaderText("¿Estás seguro de bloquear a " + selectedContact.getContactUsername() + "?");
        alert.setContentText("El contacto desaparecerá de tu lista y no podrás enviarle mensajes.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                contactService.blockContact(selectedContact);
                
                // Limpiar la vista del chat actual
                messageListView.getItems().clear();
                selectedContact = null;
                chatTitleLabel.setText("");
                sendButton.setDisable(true);
                messageInput.setDisable(true);
                if (blockButton != null) blockButton.setDisable(true);
                if (clearChatButton != null) clearChatButton.setDisable(true);
            }
        });
    }

    private void handleDeleteMessage(ChatMessage message){
        messageService.deleteOneMessage(message);
    }

    private void handleEditMessage(ChatMessage message){
        TextInputDialog dialog = new TextInputDialog(message.getContent());
        dialog.setTitle("Editar Mensaje");
        dialog.setHeaderText("Edita tu mensaje");
        dialog.setContentText("Nuevo contenido:");

        dialog.showAndWait().ifPresent(newContent -> {
            if(!newContent.trim().isEmpty() && !newContent.equals(message.getContent())){
                messageService.editMessage(message, newContent);
            }
        });
    }

    // Muestra un dialog pane para añadir contactos
    private void showAddContactDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Agregar Contacto");
        dialog.setHeaderText("Agrega un nuevo contacto");
        
        // Botones
        ButtonType addButtonType = new ButtonType("Agregar", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);
        
        // Contenido
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));
        
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username del contacto");
        
        TextField nicknameField = new TextField();
        nicknameField.setPromptText("Apodo (opcional)");
        
        Label errorLabel = new Label();
        errorLabel.setStyle("-fx-text-fill: red;");
        
        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Apodo:"), 0, 1);
        grid.add(nicknameField, 1, 1);
        grid.add(errorLabel, 1, 2);
        
        dialog.getDialogPane().setContent(grid);
        
        // Foco inicial
        Platform.runLater(() -> usernameField.requestFocus());
        
        // Validación al presionar "Agregar"
        Button addButton = (Button) dialog.getDialogPane().lookupButton(addButtonType);
        addButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String username = usernameField.getText().trim();
            String nickname = nicknameField.getText().trim();
            
            // Validar
            if (username.isEmpty()) {
                errorLabel.setText("Ingrese un username");
                event.consume();
                return;
            }
            
            if (username.length() < 3) {
                errorLabel.setText("Username mínimo 3 caracteres");
                event.consume();
                return;
            }
            
            if (username.equalsIgnoreCase(currentUsername)) {
                errorLabel.setText("No puedes agregarte a ti mismo");
                event.consume();
                return;
            }
            
            if (!username.matches("^[a-zA-Z0-9_-]+$")) {
                errorLabel.setText("Caracteres no permitidos");
                event.consume();
                return;
            }
          
            // Agregar contacto
            Contact contact = contactService.addContact(
                currentUserId, 
                username
            );
            
            if (contact == null) {
                errorLabel.setText("Error al agregar contacto");
                event.consume();
            }
        });
        
        dialog.showAndWait();
    }
    
    private void showErrorAlert(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
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

    private void showAddContactConfirmation(Contact contact) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Añadir Contacto");
        alert.setHeaderText(null);
        alert.setContentText("¿Quieres añadir este usuario a tu lista de contactos?");

        ButtonType btnYes = new ButtonType("Sí", ButtonBar.ButtonData.YES);
        ButtonType btnNo = new ButtonType("No", ButtonBar.ButtonData.NO);

        alert.getButtonTypes().setAll(btnYes, btnNo);

        alert.showAndWait().ifPresent(response -> {
            if (response == btnYes) {
                contactService.confirmContact(contact);
            }
        });
    }

    private void updateNotificationBadge() {
        if (notificationBadge != null) {
            notificationBadge.setVisible(!messageService.getNotifications().isEmpty());
        }
    }

    // Clase interna para celdas de contacto con botones
    private class ContactListCell extends ListCell<Contact> {
        private final boolean isBlockedList;

        public ContactListCell(boolean isBlockedList) {
            this.isBlockedList = isBlockedList;
        }

        @Override
        protected void updateItem(Contact contact, boolean empty) {
            super.updateItem(contact, empty);

            if (empty || contact == null) {
                setText(null);
                setGraphic(null);
            } else {
                HBox hbox = new HBox(10);
                hbox.setAlignment(Pos.CENTER_LEFT);
                
                Label nameLabel = new Label(contact.getContactUsername());
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                
                Button actionButton = new Button();
                if (isBlockedList) {
                    // Botón Desbloquear
                    actionButton.setText("🔓"); // Icono candado abierto
                    actionButton.setStyle("-fx-background-color: transparent; -fx-text-fill: green; -fx-font-size: 14px; -fx-cursor: hand;");
                    actionButton.setOnAction(e -> showUnblockConfirmation(contact));
                    hbox.getChildren().addAll(nameLabel, spacer, actionButton);
                } else {
                    // Botón Bloquear
                    actionButton.setText("🔒"); // Icono candado cerrado
                    actionButton.setStyle("-fx-background-color: transparent; -fx-text-fill: red; -fx-font-size: 14px; -fx-cursor: hand;");
                    actionButton.setOnAction(e -> showBlockConfirmation(contact));
                    
                    Button handshakeButton = new Button("🤝");
                    handshakeButton.setStyle("-fx-background-color: transparent; -fx-font-size: 14px; -fx-cursor: hand;");
                    handshakeButton.setOnAction(e -> showAddContactConfirmation(contact));
                    
                    hbox.getChildren().addAll(nameLabel, spacer, handshakeButton, actionButton);
                }
                
                setGraphic(hbox);
            }
        }
    }

    private void showBlockConfirmation(Contact contact) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Bloquear Contacto");
        alert.setHeaderText("¿Bloquear a " + contact.getContactUsername() + "?");
        alert.setContentText("No podrás recibir mensajes de este usuario.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                contactService.blockContact(contact);
                // Si el contacto bloqueado era el seleccionado, limpiar chat
                if (selectedContact != null && selectedContact.getId() == contact.getId()) {
                    messageListView.getItems().clear();
                    selectedContact = null;
                    chatTitleLabel.setText("");
                    sendButton.setDisable(true);
                    messageInput.setDisable(true);
                }
            }
        });
    }

    private void showUnblockConfirmation(Contact contact) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Desbloquear Contacto");
        alert.setHeaderText("¿Desbloquear a " + contact.getContactUsername() + "?");
        alert.setContentText("Podrás volver a intercambiar mensajes.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                contactService.unblockContact(contact);
            }
        });
    }
}
