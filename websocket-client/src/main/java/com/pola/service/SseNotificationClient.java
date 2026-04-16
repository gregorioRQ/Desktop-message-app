
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

import com.pola.event.SseEvent;
import com.pola.event.SseEventBus;
import com.pola.event.SseEventType;

/**
* Cliente SSE que se conecta al notification-service.
* Parsea los eventos SSE y los publica en el SseEventBus para ser
* procesados por los handlers registrados.
*/
public class SseNotificationClient {

    private static final String SSE_ENDPOINT = "http://localhost:8084/api/notifications/subscribe/";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RETRY_TIME_MS = 30000;
    private static final long RECONNECT_DELAY_MS = 1000;

    private final String username;
    private final String token;
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
    * Crea un nuevo cliente SSE.
    * @param username Username del usuario
    * @param token Token de autenticación (puede ser null)
    * @param onConnectCallback Callback cuando se establece la conexión
    * @param onDisconnectCallback Callback cuando se pierde la conexión
    */
    public SseNotificationClient(String username, String token,
        Runnable onConnectCallback, Runnable onDisconnectCallback) {
        this.username = username;
        this.token = token;
        this.onConnectCallback = onConnectCallback;
        this.onDisconnectCallback = onDisconnectCallback;
    }

    /**
    * Establece la conexión SSE con el notification-service.
    */
    public void connect() {
        synchronized (lock) {
            if (isConnected || isConnecting) {
                System.out.println("[SseNotificationClient] Ya conectado o conectando");
                return;
            }
            isConnecting = true;
        }

        System.out.println("[SseNotificationClient] Iniciando conexión SSE para usuario: " + username);
        startConnection();
    }

    private void startConnection() {
        try {
            String endpoint = SSE_ENDPOINT + username;
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/event-stream");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(60000);

            if (token != null && !token.isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + token.trim());
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                System.err.println("[SseNotificationClient] Conexión fallida con status: " + responseCode);
                handleConnectionError(new IOException("HTTP " + responseCode));
                return;
            }

            System.out.println("[SseNotificationClient] Conexión SSE establecida");

            readerThread = new Thread(this::readStream, "SSE-reader-" + username);
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
            System.err.println("[SseNotificationClient] Error de conexión: " + e.getMessage());
            handleConnectionError(e);
        }
    }

    private void readStream() {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {

            StringBuilder eventData = new StringBuilder();
            String currentEventName = null;
            String line;

            while (!Thread.currentThread().isInterrupted() && isConnected) {
                line = reader.readLine();

                if (line == null) {
                    break;
                }

                if (line.startsWith("event:")) {
                    // Parsear nombre del evento: "event: presence" -> "presence"
                    currentEventName = line.substring(6).trim();
                } else if (line.startsWith("data: ")) {
                    // Parsear datos con espacio: "data: {"type":"ONLINE"..." -> "{"type":"ONLINE"..."
                    eventData.append(line.substring(6)).append("\n");
                } else if (line.startsWith("data:") && !line.startsWith("data: ")) {
                    // Parsear datos sin espacio: "data:{"type":"ONLINE"..." -> "{"type":"ONLINE"..."
                    eventData.append(line.substring(5)).append("\n");
                } else if (line.isEmpty() && eventData.length() > 0) {
                    // Línea vacía indica fin del evento
                    String data = eventData.toString().trim();
                    eventData.setLength(0);

                    // Mapear nombre del evento a SseEventType y publicar
                    SseEventType eventType = mapEventNameToType(currentEventName);
                    publishEvent(eventType, data);

                    currentEventName = null;
                }
            }

        } catch (IOException e) {
            if (isConnected) {
                System.err.println("[SseNotificationClient] Error leyendo stream: " + e.getMessage());
                handleConnectionError(e);
            }
        } finally {
            if (isConnected) {
                handleConnectionError(new IOException("Stream cerrado"));
            }
        }
    }

    /**
    * Mapea el nombre del evento SSE al tipo de evento.
    * @param eventName nombre del evento (presence, heartbeat, etc.)
    * @return SseEventType correspondiente
    */
    private SseEventType mapEventNameToType(String eventName) {
        if (eventName == null || eventName.isEmpty()) {
            return SseEventType.UNKNOWN;
        }

        switch (eventName.toLowerCase()) {
        case "presence":
            return SseEventType.PRESENCE;
        case "contact_list":
            return SseEventType.CONTACT_LIST;
        case "heartbeat":
            return SseEventType.HEARTBEAT;
        case "message":
            return SseEventType.MESSAGE;
        case "notification":
            return SseEventType.NOTIFICATION;
        default:
            return SseEventType.UNKNOWN;
        }
    }

    /**
    * Publica el evento en el SseEventBus.
    * @param type tipo de evento
    * @param data datos del evento
    */
    private void publishEvent(SseEventType type, String data) {
        SseEvent event = new SseEvent.Builder()
            .type(type)
            .data(data)
            .build();

        SseEventBus.getInstance().publish(event);
    }

    private void handleConnectionError(Throwable error) {
        boolean wasConnected;
        synchronized (lock) {
            wasConnected = isConnected;
            isConnected = false;
            isConnecting = false;
        }

        if (wasConnected) {
            System.err.println("[SseNotificationClient] Conexión perdida: " + error.getMessage());

            for (Consumer<Throwable> listener : errorListeners) {
                try {
                    listener.accept(error);
                } catch (Exception e) {
                    System.err.println("[SseNotificationClient] Error en listener: " + e.getMessage());
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
            System.err.println("[SseNotificationClient] Máximo de reintentos alcanzado. Abandonando.");
            return;
        }

        retryCount++;
        long delay = RETRY_TIME_MS;

        System.out.println("[SseNotificationClient] Programando reintento " + retryCount + "/" + MAX_RETRY_ATTEMPTS
            + " en " + (delay / 1000) + " segundos...");

        retryScheduler = Executors.newSingleThreadScheduledExecutor();
        retryScheduler.schedule(() -> {
            System.out.println("[SseNotificationClient] Intentando reconexión...");
            connect();
        }, delay, TimeUnit.MILLISECONDS);
    }

    /**
    * Desconecta el cliente SSE.
    */
    public void disconnect() {
        System.out.println("[SseNotificationClient] Desconectando cliente SSE para usuario: " + username);

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
                System.err.println("[SseNotificationClient] Error desconectando: " + e.getMessage());
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

        System.out.println("[SseNotificationClient] Desconectado");
    }

    /**
    * Registra un listener para errores de conexión.
    * @param listener Consumer que procesará errores
    */
    public void addErrorListener(Consumer<Throwable> listener) {
        errorListeners.add(listener);
    }

    /**
    * Verifica si el cliente está conectado.
    * @return true si está conectado
    */
    public boolean isConnected() {
        return isConnected;
    }
}
