package com.pola.view;

import com.pola.model.Contact;
import com.pola.service.ContactService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import java.util.function.Consumer;

public class ContactListCell extends ListCell<Contact> {
    private final boolean isBlockedList;
    private final Consumer<Contact> onBlock;
    private final Consumer<Contact> onUnblock;
    private final Consumer<Contact> onConfirm;
    private final ContactService contactService;

    public ContactListCell(boolean isBlockedList, 
                           Consumer<Contact> onBlock, 
                           Consumer<Contact> onUnblock, 
                           Consumer<Contact> onConfirm,
                           ContactService contactService) {
        this.isBlockedList = isBlockedList;
        this.onBlock = onBlock;
        this.onUnblock = onUnblock;
        this.onConfirm = onConfirm;
        this.contactService = contactService;
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
            
            boolean isOnline = contact.isOnline();
            Label statusLabel = new Label("CONECTADO");
            statusLabel.setStyle("-fx-font-size: 10px; -fx-padding: 0 0 0 5;");
            statusLabel.setTextFill(isOnline ? Color.GREEN : Color.GRAY);
            
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            
            if (isBlockedList) {
                Button actionButton = new Button("🔓");
                actionButton.setStyle("-fx-background-color: transparent; -fx-text-fill: green; -fx-font-size: 14px; -fx-cursor: hand;");
                actionButton.setOnAction(e -> onUnblock.accept(contact));
                hbox.getChildren().addAll(nameLabel, statusLabel, spacer, actionButton);
            } else {
                Button actionButton = new Button("🔒");
                actionButton.setStyle("-fx-background-color: transparent; -fx-text-fill: red; -fx-font-size: 14px; -fx-cursor: hand;");
                actionButton.setOnAction(e -> onBlock.accept(contact));
                
                Button addButton = new Button("Agregar");
                addButton.setStyle("-fx-background-color: transparent; -fx-font-size: 12px; -fx-cursor: hand;");
                addButton.setOnAction(e -> onConfirm.accept(contact));
                
                hbox.getChildren().addAll(nameLabel, statusLabel, spacer, addButton, actionButton);
            }
            
            setGraphic(hbox);
        }
    }
}
