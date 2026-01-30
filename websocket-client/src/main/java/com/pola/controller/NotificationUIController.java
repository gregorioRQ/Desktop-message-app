package com.pola.controller;

import com.pola.model.Notification;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.shape.Circle;

/**
 * Componente responsable exclusivamente de la renderización y lógica de UI de las notificaciones.
 * Principio SOLID: Single Responsibility.
 */
public class NotificationUIController {

    private final ListView<Notification> notificationsListView;
    private final Circle notificationBadge;
    private final Button clearButton;
    private final ObservableList<Notification> notifications;

    public NotificationUIController(ListView<Notification> notificationsListView, 
                                    Circle notificationBadge, 
                                    Button clearButton,
                                    ObservableList<Notification> notifications) {
        this.notificationsListView = notificationsListView;
        this.notificationBadge = notificationBadge;
        this.clearButton = clearButton;
        this.notifications = notifications;
        
        initialize();
    }

    private void initialize() {
        if (notificationsListView != null) {
            notificationsListView.setItems(notifications);
        }
        
        if (clearButton != null) {
            clearButton.setOnAction(e -> notifications.clear());
            // El botón solo es visible/habilitado si hay notificaciones
            clearButton.disableProperty().bind(Bindings.isEmpty(notifications));
        }

        updateBadgeVisibility();
        
        notifications.addListener((ListChangeListener<Notification>) c -> Platform.runLater(this::updateBadgeVisibility));
    }

    private void updateBadgeVisibility() {
        if (notificationBadge != null) {
            notificationBadge.setVisible(!notifications.isEmpty());
        }
    }
}