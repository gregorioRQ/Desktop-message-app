package com.pola.view;

import java.util.function.Consumer;

import com.pola.model.ChatMessage;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

public class MessageListCell extends ListCell<ChatMessage>{
    private String currentUsername;
    private final Consumer<ChatMessage> onDelete;
    private final Consumer<ChatMessage> onEdit;

    public MessageListCell(String currentUsername, Consumer<ChatMessage> onDelete, Consumer<ChatMessage> onEdit){
        this.currentUsername = currentUsername;
        this.onDelete = onDelete;
        this.onEdit = onEdit;
    }

    @Override
    protected void updateItem(ChatMessage message, boolean empty){
        super.updateItem(message, empty);

        if(empty || message == null){
            setText(null);
            setGraphic(null);
        } else{
            String sender = message.getSenderId().equals(currentUsername) ? "Tu" : "Contacto";
            String text = String.format("[%s] %s: %s", message.getFormattedTime(), sender, message.getContent());
            
            // Crear un HBox con el contenido del mensaje
            HBox messageBox = new HBox(10);
            messageBox.setAlignment(Pos.CENTER_LEFT);
            messageBox.setStyle("-fx-padding: 5; -fx-border-radius: 5;");
            
            // Label con el contenido del mensaje
            Label messageLabel = new Label(text);
            messageLabel.setWrapText(true);
            HBox.setHgrow(messageLabel, Priority.ALWAYS);
            
            messageBox.getChildren().add(messageLabel);
            
            // Si es un mensaje propio, agregar botones de acción
            if(message.getSenderId().equals(currentUsername)){
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                messageBox.getChildren().add(spacer);
                
                // Botón editar (lápiz)
                Button editButton = new Button("✎");
                editButton.setStyle("-fx-font-size: 12; -fx-padding: 5; -fx-min-width: 30; -fx-text-fill: #3498db;");
                editButton.setOnAction(e -> {
                    if(onEdit != null){
                        onEdit.accept(message);
                    }
                });
                
                // Botón eliminar (X roja)
                Button deleteButton = new Button("✕");
                deleteButton.setStyle("-fx-font-size: 12; -fx-padding: 5; -fx-min-width: 30; -fx-text-fill: #e74c3c;");
                deleteButton.setOnAction(e -> {
                    if(onDelete != null){
                        onDelete.accept(message);
                    }
                });
                
                messageBox.getChildren().addAll(editButton, deleteButton);
            }
            
            setText(null);
            setGraphic(messageBox);
        }
    }
}

