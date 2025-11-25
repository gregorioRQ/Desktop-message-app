package com.pola;

import java.net.URI;
import java.net.http.WebSocket.Listener;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ClientEndpoint
public class StompClient {
    private Session session;
    private String username;
    ChatClient client;

    // Preferred constructor: inject a MessageHandler
    public StompClient(String username) throws Exception {
        this.username = username;
        this.connect("ws://localhost:8085/ws-chat");
    }

    public void connect(String uri) throws Exception {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.connectToServer(this, new URI(uri));
    }

    @OnOpen
    public void onOpen(Session session) {
        this.session = session;
        System.out.println(username + " conectado al servidor WebSocket.");

        // 1) CONNECT
        String connectFrame = "CONNECT\n" +
                "accept-version:1.2\n" +
                "host:localhost\n\n" + '\u0000';
        sendRawFrame(connectFrame);

        // topic para recibir notificaciones de cualquier tipo para un usuario
        // específico
        String subscribeFrame = "SUBSCRIBE\n" +
                "id:sub-" + username + "\n" +
                "destination:/topic/notifications/" + username + "\n\n" + '\u0000';
        sendRawFrame(subscribeFrame);

        // Topic para mensajes vistos
        String subscribeSeenFrame = "SUBSCRIBE\n" +
                "id:sub-seen-" + username + "\n" +
                "destination:/topic/seen/" + username + "\n\n" + '\u0000';
        sendRawFrame(subscribeSeenFrame);
    }

    // cuando llegue un frame de texto desde el servidor el metodo
    // lo pasa como String
    @OnMessage
    public void onMessage(String raw) {
        System.out.println("Mensaje recibido: " + raw);
        try {
            // Normalize line endings
            String normalized = raw.replace("\r\n", "\n");

            // STOMP frames start with a command (e.g. CONNECTED, MESSAGE, RECEIPT,
            // ERROR)
            int firstNewline = normalized.indexOf('\n');
            String command = (firstNewline == -1) ? normalized.trim() : normalized.substring(0, firstNewline).trim();

            if ("CONNECTED".equals(command)) {
                // Server acknowledged the CONNECT frame; no JSON body here.
                System.out.println("STOMP CONNECTED frame received");
                return;
            }

            if ("MESSAGE".equals(command)) {
                // Separate headers from body: headers end at the first blank line ("\n\n")
                String body = extractStompBody(normalized);

                if (body == null || body.isEmpty()) {
                    System.out.println("MESSAGE frame without body");
                    return;
                }

                // body may contain the JSON payload
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(body);

                String type = root.has("type") ? root.get("type").asText() : null;
                // payload can be either nested under 'payload' or the whole object itself

                if (type != null && "MESSAGE_SENT".equals(type)) {

                } else {
                    System.out.println("Mensaje MESSAGE con payload inesperado o sin campo 'type'");
                }

                return;
            }

            // ignore other STOMP control frames or log them
            System.out.println("STOMP frame command: " + command + " (ignored)");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Extract the body from a STOMP frame: find the first blank line (headers/body
    // separator)
    // and return the body trimmed and without the trailing null terminator if
    // present.
    private String extractStompBody(String normalizedFrame) {
        int separator = normalizedFrame.indexOf("\n\n");
        if (separator == -1) {
            // no headers/body separator found
            return null;
        }

        int bodyStart = separator + 2;
        int nullPos = normalizedFrame.indexOf('\u0000', bodyStart);
        String body = (nullPos == -1) ? normalizedFrame.substring(bodyStart)
                : normalizedFrame.substring(bodyStart, nullPos);
        return body.trim();
    }

    // previously there was a fallback handler here; now we delegate to
    // MessageListener or ListenerRegister

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        System.out.println("Conexión cerrada: " + reason);
    }

    @OnError
    public void onError(Session session, Throwable thr) {
        thr.printStackTrace();
    }

    private void sendRawFrame(String frame) {
        try {
            session.getBasicRemote().sendText(frame);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Método para publicar un evento "mensaje visto"
    public void markMessageAsSeen(String messageId) {
        String payload = String.format("{\"messageId\":\"%s\", \"receiver\":\"%s\"}",
                messageId, username);

        String sendFrame = "SEND\n" +
                "destination:/app/messageSeen\n" +
                "content-type:application/json\n\n" +
                payload + '\u0000';

        sendRawFrame(sendFrame);
        System.out.println(username + " marcó como visto el mensaje " + messageId);
    }

    public static void main(String[] args) throws Exception {

        StompClient client = new StompClient("Maria");
        // client.markMessageAsSeen("28");

        // client.markMessageAsSeen("14");

        // Mantener la app viva para escuchar notificaciones
        Thread.sleep(1000000);
    }
}
