package com.pola.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class SseNotificationClient {

    private static final String SSE_ENDPOINT = "http://localhost:8084/api/notifications/subscribe/";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_TIME_MS = 30000;
    private static final long RECONNECT_DELAY_MS = 1000;

    private final String userId;
    private final String token;
    private final CopyOnWriteArrayList<Consumer<String>> messageListeners = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Consumer<Throwable>> errorListeners = new CopyOnWriteArrayList<>();
    private final Runnable onConnectCallback;
    private final Runnable onDisconnectCallback;

    private Thread readerThread;
    private HttpURLConnection connection;
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private int retryCount = 0;
    private ScheduledExecutorService retryScheduler;

    private final Object lock = new Object();

    /**
     * Creates a new SSE notification client.
     * 
     * This client connects to the notification-service via SSE to receive
     * real-time push notifications. It includes automatic reconnection logic.
     * 
     * @param userId              The unique identifier of the user
     * @param token              Authentication token (can be null)
     * @param onConnectCallback  Callback executed when connection is established
     * @param onDisconnectCallback Callback executed when connection is lost
     */
    public SseNotificationClient(String userId, String token, 
                                  Runnable onConnectCallback, Runnable onDisconnectCallback) {
        this.userId = userId;
        this.token = token;
        this.onConnectCallback = onConnectCallback;
        this.onDisconnectCallback = onDisconnectCallback;
    }

    /**
     * Establishes SSE connection to the notification service.
     * 
     * This method connects to the SSE endpoint and starts a background thread
     * to read the event stream. If the connection fails, it will retry up to
     * MAX_RETRY_ATTEMPTS times within RETRY_TIME_MS.
     */
    public void connect() {
        synchronized (lock) {
            if (isConnected || isConnecting) {
                System.out.println("[SseNotificationClient] Already connected or connecting");
                return;
            }
            isConnecting = true;
        }

        System.out.println("[SseNotificationClient] Starting SSE connection for user: " + userId);
        startConnection();
    }

    private void startConnection() {
        try {
            String endpoint = SSE_ENDPOINT + userId;
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(60000);

            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token.trim());
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                System.err.println("[SseNotificationClient] Connection failed with status: " + responseCode);
                handleConnectionError(new IOException("HTTP " + responseCode));
                return;
            }

            System.out.println("[SseNotificationClient] SSE connection established");

            readerThread = new Thread(this::readStream, "SSE-reader-" + userId);
            readerThread.setDaemon(true);
            readerThread.start();

            synchronized (lock) {
                isConnected = true;
                isConnecting = false;
                retryCount = 0;
            }

            if (onConnectCallback != null) {
                onConnectCallback.run();
            }

        } catch (IOException e) {
            System.err.println("[SseNotificationClient] Connection error: " + e.getMessage());
            handleConnectionError(e);
        }
    }

    private void readStream() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder eventData = new StringBuilder();
            String line;

            while (!Thread.currentThread().isInterrupted() && isConnected) {
                line = reader.readLine();
                
                System.out.println("[SseNotificationClient] Raw line received: '" + line + "'");
                
                if (line == null) {
                    break;
                }

                if (line.startsWith("data: ")) {
                    eventData.append(line.substring(6)).append("\n");
                } else if (line.isEmpty() && eventData.length() > 0) {
                    String message = eventData.toString().trim();
                    eventData.setLength(0);
                    processMessage(message);
                }
            }

        } catch (IOException e) {
            if (isConnected) {
                System.err.println("[SseNotificationClient] Stream read error: " + e.getMessage());
                handleConnectionError(e);
            }
        } finally {
            if (isConnected) {
                handleConnectionError(new IOException("Stream closed"));
            }
        }
    }

    private void processMessage(String message) {
        if (message == null || message.isEmpty()) {
            return;
        }

        System.out.println("[SseNotificationClient] Received message: " + message);

        for (Consumer<String> listener : messageListeners) {
            try {
                listener.accept(message);
            } catch (Exception e) {
                System.err.println("[SseNotificationClient] Error in message listener: " + e.getMessage());
            }
        }
    }

    private void handleConnectionError(Throwable error) {
        boolean wasConnected;
        synchronized (lock) {
            wasConnected = isConnected;
            isConnected = false;
            isConnecting = false;
        }

        if (wasConnected) {
            System.err.println("[SseNotificationClient] Connection lost: " + error.getMessage());

            for (Consumer<Throwable> listener : errorListeners) {
                try {
                    listener.accept(error);
                } catch (Exception e) {
                    System.err.println("[SseNotificationClient] Error in error listener: " + e.getMessage());
                }
            }

            if (onDisconnectCallback != null) {
                onDisconnectCallback.run();
            }

            scheduleRetry();
        }
    }

    private void scheduleRetry() {
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            System.err.println("[SseNotificationClient] Max retry attempts reached. Giving up.");
            return;
        }

        retryCount++;
        long delay = RECONNECT_DELAY_MS * retryCount;
        long totalDelay = RETRY_TIME_MS;

        System.out.println("[SseNotificationClient] Scheduling retry " + retryCount + "/" + MAX_RETRY_ATTEMPTS 
                + " in " + (totalDelay / 1000) + " seconds...");

        retryScheduler = Executors.newSingleThreadScheduledExecutor();
        retryScheduler.schedule(() -> {
            System.out.println("[SseNotificationClient] Attempting reconnection...");
            connect();
        }, totalDelay, TimeUnit.MILLISECONDS);
    }

    /**
     * Disconnects the SSE client.
     * 
     * This stops the connection and cancels any pending retry attempts.
     */
    public void disconnect() {
        System.out.println("[SseNotificationClient] Disconnecting SSE client for user: " + userId);

        synchronized (lock) {
            isConnected = false;
            isConnecting = false;
        }

        if (readerThread != null) {
            readerThread.interrupt();
            readerThread = null;
        }

        if (connection != null) {
            try {
                connection.disconnect();
            } catch (Exception e) {
                System.err.println("[SseNotificationClient] Error disconnecting: " + e.getMessage());
            }
            connection = null;
        }

        if (retryScheduler != null) {
            retryScheduler.shutdownNow();
            retryScheduler = null;
        }

        if (onDisconnectCallback != null) {
            onDisconnectCallback.run();
        }

        System.out.println("[SseNotificationClient] Disconnected");
    }

    /**
     * Registers a listener for incoming messages.
     * 
     * @param listener Consumer that processes received messages
     */
    public void addMessageListener(Consumer<String> listener) {
        messageListeners.add(listener);
    }

    /**
     * Registers a listener for connection errors.
     * 
     * @param listener Consumer that processes errors
     */
    public void addErrorListener(Consumer<Throwable> listener) {
        errorListeners.add(listener);
    }

    /**
     * Checks if the client is currently connected.
     * 
     * @return true if connected, false otherwise
     */
    public boolean isConnected() {
        return isConnected;
    }
}
