package com.pola.view;

import com.pola.model.ChatMessage;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/**
 * Celda personalizada para mostrar mensajes de chat con indicador de estado.
 */
public class MessageListCell extends ListCell<ChatMessage> {
    private final String currentUsername;
    private final Consumer<ChatMessage> onDelete;
    private final Consumer<ChatMessage> onEdit;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

    public MessageListCell(String currentUsername, Consumer<ChatMessage> onDelete, Consumer<ChatMessage> onEdit) {
        this.currentUsername = currentUsername;
        this.onDelete = onDelete;
        this.onEdit = onEdit;
    }

    @Override
    protected void updateItem(ChatMessage message, boolean empty) {
        super.updateItem(message, empty);

        if (empty || message == null) {
            setText(null);
            setGraphic(null);
            setStyle("-fx-background-color: transparent;");
        } else {
            // Contenedor principal
            HBox root = new HBox(10);
            VBox contentBox = new VBox(2);
            
            // Contenido del mensaje
            Label msgLabel = new Label(message.getContent());
            msgLabel.setWrapText(true);
            msgLabel.setMaxWidth(300); // Limitar ancho para que haga wrap
            
            // Hora
            Label timeLabel = new Label(message.getTimestamp().format(formatter));
            timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

            // Indicador de estado (Círculo)
            Circle statusCircle = new Circle(4);
            
            boolean isMe = message.getSenderId().equals(currentUsername);
            boolean isSystem = "Sistema".equals(message.getSenderId());

            if (isSystem) {
                // Estilo para mensajes del sistema
                root.setAlignment(Pos.CENTER);
                contentBox.setAlignment(Pos.CENTER);
                msgLabel.setStyle("-fx-background-color: #e0e0e0; -fx-padding: 5 10; -fx-background-radius: 10; -fx-font-style: italic;");
                contentBox.getChildren().add(msgLabel);
                root.getChildren().add(contentBox);
            } else if (isMe) {
                // Mensajes enviados por mí (Alineados a la derecha)
                root.setAlignment(Pos.CENTER_RIGHT);
                contentBox.setAlignment(Pos.CENTER_RIGHT);
                msgLabel.setStyle("-fx-background-color: #DCF8C6; -fx-padding: 8; -fx-background-radius: 10;");
                
                // Lógica del indicador: Verde = Visto, Rojo = No visto
                if (message.isRead()) {
                    statusCircle.setFill(Color.GREEN);
                } else {
                    statusCircle.setFill(Color.RED);
                }
                
                // Añadir menú contextual para editar/eliminar
                setupContextMenu(root, message, true);
                
                contentBox.getChildren().addAll(msgLabel, timeLabel);
                // Orden: Texto -> Círculo
                root.getChildren().addAll(contentBox, statusCircle);
            } else {
                // Mensajes recibidos (Alineados a la izquierda)
                root.setAlignment(Pos.CENTER_LEFT);
                contentBox.setAlignment(Pos.CENTER_LEFT);
                msgLabel.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 8; -fx-background-radius: 10; -fx-border-color: #E0E0E0; -fx-border-radius: 10;");
                
                setupContextMenu(root, message, false);
                
                contentBox.getChildren().addAll(msgLabel, timeLabel);
                root.getChildren().add(contentBox);
            }

            setGraphic(root);
            setStyle("-fx-background-color: transparent;");
        }
    }

    private void setupContextMenu(javafx.scene.Node node, ChatMessage message, boolean isMe) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem deleteItem = new MenuItem("Eliminar");
        deleteItem.setOnAction(e -> onDelete.accept(message));
        contextMenu.getItems().add(deleteItem);

        if (isMe) {
            MenuItem editItem = new MenuItem("Editar");
            editItem.setOnAction(e -> onEdit.accept(message));
            contextMenu.getItems().add(editItem);
        }
        
        node.setOnContextMenuRequested(e -> contextMenu.show(node, e.getScreenX(), e.getScreenY()));
    }
}
