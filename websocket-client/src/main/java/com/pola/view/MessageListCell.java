package com.pola.view;

import java.util.function.Consumer;

import org.glassfish.grizzly.Context;

import com.pola.model.ChatMessage;

import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;

public class MessageListCell extends ListCell<ChatMessage>{
    private String currentUserId;
    private final Consumer<ChatMessage> onDelete;
    private final Consumer<ChatMessage> onEdit;

    public MessageListCell(String currentUserId, Consumer<ChatMessage> onDelete, Consumer<ChatMessage> onEdit){
        this.currentUserId = currentUserId;
        this.onDelete = onDelete;
        this.onEdit = onEdit;
    }

    @Override
    protected void updateItem(ChatMessage message, boolean empty){
        super.updateItem(message, empty);

        if(empty || message == null){
            setText(null);
            setGraphic(null);
            setContextMenu(null);
        } else{
            String sender = message.getSenderId().equals(currentUserId) ? "Tu" : "Contacto";
            String text = String.format("[%s] %s: %s", message.getFormattedTime(), sender, message.getContent());

            setText(text);

            // muestra el menu contextual solo si es un mensaje propio
            if(message.getSenderId().equals(currentUserId)){
                ContextMenu contextMenu = createContextMenu(message);
                setContextMenu(contextMenu);

                setStyle("-fx-cursor: hand;");
            }else{
                setContextMenu(null);
                setStyle("");
            }
        }
    }

    private ContextMenu createContextMenu(ChatMessage message) {
        ContextMenu contextMenu = new ContextMenu();

        // Opcion eliminar
        MenuItem deleteItem = new MenuItem("Eliminar");
        deleteItem.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        deleteItem.setOnAction(e -> {
            if(onDelete != null){
                onDelete.accept(message);
            } 
        });

        // opcion editar
        MenuItem editItem = new MenuItem("Editar");
        editItem.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
        editItem.setOnAction(e -> {
            if(onEdit != null){
                onEdit.accept(message);
            }
        });

        contextMenu.getItems().addAll(deleteItem, editItem);
        return contextMenu;
    }

}
