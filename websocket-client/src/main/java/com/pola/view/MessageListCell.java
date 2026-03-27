package com.pola.view;

import com.pola.model.ChatMessage;
import com.pola.model.ImageChatMessage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

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
        } else if (message instanceof ImageChatMessage imageMessage) {
            setGraphic(createImageMessageNode(imageMessage));
            setStyle("-fx-background-color: transparent;");
        } else {
            setGraphic(createTextMessageNode(message));
            setStyle("-fx-background-color: transparent;");
        }
    }

    private javafx.scene.Node createImageMessageNode(ImageChatMessage message) {
        HBox root = new HBox(10);
        VBox contentBox = new VBox(4);
        
        boolean isMe = message.getSenderId().equals(currentUsername);
        
        if (isMe) {
            root.setAlignment(Pos.CENTER_RIGHT);
            contentBox.setAlignment(Pos.CENTER_RIGHT);
        } else {
            root.setAlignment(Pos.CENTER_LEFT);
            contentBox.setAlignment(Pos.CENTER_LEFT);
        }
        
        Label iconLabel = new Label("🖼️");
        iconLabel.setFont(new Font(24));
        
        Label photoLabel = new Label("[Foto]");
        photoLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 14px;");
        
        Label dimensionsLabel = new Label(message.getOriginalWidth() + "x" + message.getOriginalHeight());
        dimensionsLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");
        
        Label timeLabel = new Label(message.getTimestamp().format(formatter));
        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");
        
        Button downloadButton = new Button("Descargar");
        downloadButton.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-background-radius: 5; -fx-padding: 5 10;");
        downloadButton.setOnAction(e -> {
            System.out.println("Descargar imagen: " + message.getFullImageUrl());
        });
        
        contentBox.getChildren().addAll(iconLabel, photoLabel, dimensionsLabel, downloadButton, timeLabel);
        
        setupContextMenu(contentBox, message, isMe);
        
        root.getChildren().add(contentBox);
        
        return root;
    }

    private javafx.scene.Node createTextMessageNode(ChatMessage message) {
        HBox root = new HBox(10);
        VBox contentBox = new VBox(2);
        
        Label msgLabel = new Label(message.getContent());
        msgLabel.setWrapText(true);
        msgLabel.setMaxWidth(300);
        
        Label timeLabel = new Label(message.getTimestamp().format(formatter));
        timeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

        Circle statusCircle = new Circle(4);
        
        boolean isMe = message.getSenderId().equals(currentUsername);
        boolean isSystem = "Sistema".equals(message.getSenderId());

        if (isSystem) {
            root.setAlignment(Pos.CENTER);
            contentBox.setAlignment(Pos.CENTER);
            msgLabel.setStyle("-fx-background-color: #e0e0e0; -fx-padding: 5 10; -fx-background-radius: 10; -fx-font-style: italic;");
            contentBox.getChildren().add(msgLabel);
            root.getChildren().add(contentBox);
        } else if (isMe) {
            root.setAlignment(Pos.CENTER_RIGHT);
            contentBox.setAlignment(Pos.CENTER_RIGHT);
            msgLabel.setStyle("-fx-background-color: #DCF8C6; -fx-padding: 8; -fx-background-radius: 10;");
            
            if (message.isRead()) {
                statusCircle.setFill(Color.GREEN);
            } else {
                statusCircle.setFill(Color.RED);
            }
            
            setupContextMenu(root, message, true);
            
            contentBox.getChildren().addAll(msgLabel, timeLabel);
            root.getChildren().addAll(contentBox, statusCircle);
        } else {
            root.setAlignment(Pos.CENTER_LEFT);
            contentBox.setAlignment(Pos.CENTER_LEFT);
            msgLabel.setStyle("-fx-background-color: #FFFFFF; -fx-padding: 8; -fx-background-radius: 10; -fx-border-color: #E0E0E0; -fx-border-radius: 10;");
            
            setupContextMenu(root, message, false);
            
            contentBox.getChildren().addAll(msgLabel, timeLabel);
            root.getChildren().add(contentBox);
        }

        return root;
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
