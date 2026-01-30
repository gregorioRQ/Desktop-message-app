package com.pola.service;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.websocket.Session;

public class NotificationHelper {

    private final Session session;

    public NotificationHelper(Session session) {
        this.session = session;
    }

    public void sendMessage(String msg) {
        try {
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(msg);
            }
        } catch (IOException e) {
            System.err.println("Error enviando mensaje STOMP: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendConnectFrame(String userId) {
        // Frame CONNECT de STOMP 1.1/1.2
        // Desactivamos heart-beat (0,0) para simplificar la implementación manual
        String connectFrame = "CONNECT\n" +
                              "accept-version:1.1,1.0\n" +
                              "userId:" + userId + "\n" +
                              "heart-beat:0,0\n" +
                              "\n" +
                              "\u0000";
        sendMessage(connectFrame);
    }

    public void sendAddContactNotification(String fromUserId, String toUserId) {
        String jsonBody = String.format("{\"from\": \"%s\", \"to\": \"%s\"}", fromUserId, toUserId);
        String sendFrame = "SEND\n" +
                           "destination:/app/contact.add\n" +
                           "type:contact_added_you\n" +
                           "content-type:application/json\n" +
                           "\n" +
                           jsonBody + "\n" +
                           "\u0000";
        sendMessage(sendFrame);
    }

    public void sendUserCreateNotification(String userId) {
        String jsonBody = String.format("{\"user_id\": \"%s\"}", userId);
        String sendFrame = "SEND\n" +
                           "destination:/app/user.add\n" +
                           "type:user_created\n" +
                           "content-type:application/json\n" +
                           "\n" +
                           jsonBody + "\n" +
                           "\u0000";
        sendMessage(sendFrame);
    }

    public void sendUserOnlineNotification(String userId) {
        String jsonBody = String.format("{\"userId\": \"%s\"}", userId);
        String sendFrame = "SEND\n" +
                           "destination:/app/user.online\n" +
                           "type:user_online\n" +
                           "content-type:application/json\n" +
                           "\n" +
                           jsonBody + "\n" +
                           "\u0000";
        sendMessage(sendFrame);
    }

    public String buildSubscribeFrame(String subscriptionId, String destination) {
        return "SUBSCRIBE\n" +
               "id:" + subscriptionId + "\n" +
               "destination:" + destination + "\n" +
               "receipt:receipt-" + subscriptionId + "\n" +
               "\n" +
               "\u0000";
    }

    public String extractHeader(String headers, String key) {
        for (String line : headers.split("\n")) {
            if (line.startsWith(key + ":")) {
                return line.substring((key + ":").length()).trim();
            }
        }
        return null;
    }

    public void handlePresenceMessage(String jsonBody, boolean isOnline, BiConsumer<String, Boolean> presenceListener) {
        if (presenceListener != null) {
            String userId = extractJsonValue(jsonBody, "userId");
            if (userId != null) {
                presenceListener.accept(userId, isOnline);
            }
        }
    }

    private String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\s*:\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
}
