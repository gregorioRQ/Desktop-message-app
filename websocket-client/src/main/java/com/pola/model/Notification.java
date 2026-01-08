package com.pola.model;

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
}
