package com.pola.model;

import java.util.Optional;
import javafx.collections.ObservableList;

public class Notification {
    private String senderUsername;
    private int count;

    public Notification(String senderUsername, int count) {
        this.senderUsername = senderUsername;
        this.count = count;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public void incrementCount() {
        this.count++;
    }

    public int getCount() {
        return count;
    }

    @Override
    public String toString() {
        // Formato: "Usuario X te ha enviado 5 mensajes"
        return "Usuario " + senderUsername + " te ha enviado " + count + " mensaje" + (count > 1 ? "s" : "");
    }

    public static void updateOrAdd(ObservableList<Notification> notifications, String senderUsername) {
        Optional<Notification> existing = notifications.stream()
            .filter(n -> n.getSenderUsername().equals(senderUsername))
            .findFirst();
        
        if (existing.isPresent()) {
            Notification n = existing.get();
            n.incrementCount();
            // Forzar actualización en la lista (reemplazando el elemento)
            int idx = notifications.indexOf(n);
            notifications.set(idx, n);
        } else {
            notifications.add(new Notification(senderUsername, 1));
        }
    }
}
