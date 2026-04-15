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
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
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
    private final Consumer<ImageChatMessage> onDownloadImage;
    private final Consumer<ImageChatMessage> onViewImage;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");

    public MessageListCell(String currentUsername, Consumer<ChatMessage> onDelete, 
                          Consumer<ChatMessage> onEdit, Consumer<ImageChatMessage> onDownloadImage,
                          Consumer<ImageChatMessage> onViewImage) {
        this.currentUsername = currentUsername;
        this.onDelete = onDelete;
        this.onEdit = onEdit;
        this.onDownloadImage = onDownloadImage;
        this.onViewImage = onViewImage;
    }

    @Override
    protected void updateItem(ChatMessage message, boolean empty) {
        super.updateItem(message, empty);

        if (empty || message == null) {
            setText(null);
            setGraphic(null);
            setStyle("-fx-background-color: transparent;");
        } else if (message instanceof ImageChatMessage imageMessage) {
            setGraphic(createImageMessageNode(imageMessage, onDownloadImage, onViewImage));
            setStyle("-fx-background-color: transparent;");
        } else {
            setGraphic(createTextMessageNode(message));
            setStyle("-fx-background-color: transparent;");
        }
    }

    private javafx.scene.Node createImageMessageNode(ImageChatMessage message, 
                                                      Consumer<ImageChatMessage> onDownload,
                                                      Consumer<ImageChatMessage> onView) {
        VBox vbox = new VBox(5);
        vbox.setAlignment(Pos.CENTER_LEFT);
        vbox.setPadding(new Insets(5));

        String imageUrl = message.getFullImageUrl();
        if (imageUrl == null || imageUrl.isEmpty()) {
            imageUrl = "https://via.placeholder.com/150?text=No+Image";
        }

        try {
            Image image = new Image(imageUrl, 150, 100, true, true);
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(150);
            imageView.setFitHeight(100);
            imageView.setPreserveRatio(true);

            imageView.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && onView != null) {
                    onView.accept(message);
                }
            });
            imageView.setCursor(javafx.scene.Cursor.HAND);

            Label timestampLabel = new Label(message.getTimestamp().format(formatter));
            timestampLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: gray;");

            Button downloadBtn = new Button("Descargar");
            downloadBtn.setStyle("-fx-font-size: 10px;");
            downloadBtn.setOnAction(e -> {
                if (onDownload != null) {
                    onDownload.accept(message);
                }
            });

            HBox buttonBox = new HBox(10, timestampLabel, downloadBtn);
            buttonBox.setAlignment(Pos.CENTER_LEFT);

            vbox.getChildren().addAll(imageView, buttonBox);
        } catch (Exception e) {
            System.err.println("Error loading image: " + e.getMessage());
            Label errorLabel = new Label("Imagen no disponible");
            errorLabel.setStyle("-fx-text-fill: gray; -fx-font-style: italic;");
            vbox.getChildren().add(errorLabel);
        }

        return vbox;
    }

    private javafx.scene.Node createTextMessageNode(ChatMessage message) {
        HBox root = new HBox(10);
        root.setPadding(new Insets(5));
        
        VBox contentBox = new VBox(2);
        
        Label msgLabel = new Label(message.getContent());
        msgLabel.setFont(new Font(13));
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
            
            ChatMessage.MessageStatus status = message.getStatus();
            
            if (status == ChatMessage.MessageStatus.READ) {
                statusCircle.setFill(Color.GREEN);
            } else if (status == ChatMessage.MessageStatus.FAILED) {
                statusCircle.setFill(Color.RED);
            } else if (status == ChatMessage.MessageStatus.DELIVERED) {
                statusCircle.setFill(Color.GREEN);
            } else {
                statusCircle.setFill(Color.GOLD);
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