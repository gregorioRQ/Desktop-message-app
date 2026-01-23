package com.pola.view;

import com.pola.model.Contact;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import java.util.function.Consumer;

public class ContactListCell extends ListCell<Contact> {
    private final boolean isBlockedList;
    private final Consumer<Contact> onBlock;
    private final Consumer<Contact> onUnblock;
    private final Consumer<Contact> onConfirm;

    public ContactListCell(boolean isBlockedList, 
                           Consumer<Contact> onBlock, 
                           Consumer<Contact> onUnblock, 
                           Consumer<Contact> onConfirm) {
        this.isBlockedList = isBlockedList;
        this.onBlock = onBlock;
        this.onUnblock = onUnblock;
        this.onConfirm = onConfirm;
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
            
            if (isBlockedList) {
                Button actionButton = new Button("🔓");
                actionButton.setStyle("-fx-background-color: transparent; -fx-text-fill: green; -fx-font-size: 14px; -fx-cursor: hand;");
                actionButton.setOnAction(e -> onUnblock.accept(contact));
                hbox.getChildren().addAll(nameLabel, spacer, actionButton);
            } else {
                Button actionButton = new Button("🔒");
                actionButton.setStyle("-fx-background-color: transparent; -fx-text-fill: red; -fx-font-size: 14px; -fx-cursor: hand;");
                actionButton.setOnAction(e -> onBlock.accept(contact));
                
                Button handshakeButton = new Button("🤝");
                handshakeButton.setStyle("-fx-background-color: transparent; -fx-font-size: 14px; -fx-cursor: hand;");
                handshakeButton.setId("handshakeButton");
                handshakeButton.setOnAction(e -> onConfirm.accept(contact));
                
                hbox.getChildren().addAll(nameLabel, spacer, handshakeButton, actionButton);
            }
            
            setGraphic(hbox);
        }
    }
}
