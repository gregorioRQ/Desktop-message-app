package com.pola.view;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class ChatDialogs {

    public static void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public static void showInfo(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public static void showConfirmation(String title, String header, String content, Runnable onConfirm) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);

            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    onConfirm.run();
                }
            });
        });
    }

    public static void showClearChatDialog(String contactName, Runnable onClearLocal, Runnable onClearGlobal) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Vaciar Chat");
            alert.setHeaderText("¿Deseas vaciar el chat con " + contactName + "?");
            alert.setContentText("Selecciona una opción:");

            ButtonType btnMe = new ButtonType("Solo para mí");
            ButtonType btnAll = new ButtonType("Para todos");
            ButtonType btnCancel = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(btnMe, btnAll, btnCancel);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == btnMe) {
                    onClearLocal.run();
                } else if (result.get() == btnAll) {
                    onClearGlobal.run();
                }
            }
        });
    }

    public static void showEditMessageDialog(String currentContent, Consumer<String> onEdit) {
        Platform.runLater(() -> {
            TextInputDialog dialog = new TextInputDialog(currentContent);
            dialog.setTitle("Editar Mensaje");
            dialog.setHeaderText("Edita tu mensaje");
            dialog.setContentText("Nuevo contenido:");

            dialog.showAndWait().ifPresent(newContent -> {
                if(!newContent.trim().isEmpty() && !newContent.equals(currentContent)){
                    onEdit.accept(newContent);
                }
            });
        });
    }

    public static void showAddContactDialog(String currentUsername, Function<String, Boolean> onAdd) {
        Platform.runLater(() -> {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Agregar Contacto");
            dialog.setHeaderText("Agrega un nuevo contacto");
            
            ButtonType addButtonType = new ButtonType("Agregar", ButtonBar.ButtonData.OK_DONE);
            dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);
            
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
            Platform.runLater(usernameField::requestFocus);
            
            Button addButton = (Button) dialog.getDialogPane().lookupButton(addButtonType);
            addButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                String username = usernameField.getText().trim();
                
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
                
                if (!onAdd.apply(username)) {
                    errorLabel.setText("Error al agregar contacto");
                    event.consume();
                }
            });
            
            dialog.showAndWait();
        });
    }
}

