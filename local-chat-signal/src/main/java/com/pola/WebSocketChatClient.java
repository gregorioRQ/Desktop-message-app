package com.pola;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import com.basic_chat.proto.PaqueteDatos;
import com.basic_chat.proto.PaqueteDatos.Tipo;

public class WebSocketChatClient implements WebSocket.Listener{
private WebSocket webSocket;
    private final String userId = "ClienteWebSkt_" + System.currentTimeMillis();

    public void connect(String serverUri) {
        HttpClient client = HttpClient.newHttpClient();

        System.out.println("Intentando conectar a: " + serverUri);

        try {
            // CORRECCIÓN: newWebSocketBuilder() no toma argumentos.
            // La URI se pasa solo a buildAsync().
            client.newWebSocketBuilder() 
                  .buildAsync(URI.create(serverUri), this)
                  .get(5, TimeUnit.SECONDS); // Espera 5 segundos por la conexión
        } catch (Exception e) {
            System.err.println("Error al conectar al servidor: " + e.getMessage());
        }
    }

    /**
     * Manda un mensaje de chat al servidor, serializado como binario Protobuf.
     */
    public void sendChatMessage(String content) {
        if (webSocket == null) {
            System.err.println("No se puede enviar. WebSocket no está conectado.");
            return;
        }

        try {
            // 1. Crear el mensaje Protobuf
            PaqueteDatos message = PaqueteDatos.newBuilder()
                    .setUsuarioId(userId)
                    .setContenido(content)
                    .setTimestamp(System.currentTimeMillis())
                    .setTipo(Tipo.CHAT) // Usamos la enumeración anidada
                    .build();

            // 2. Serializar el mensaje a bytes
            byte[] messageBytes = message.toByteArray();

            // 3. Enviar los bytes como un mensaje binario a través de WebSocket
            System.out.println("Enviando mensaje (" + messageBytes.length + " bytes): " + content);
            webSocket.sendBinary(ByteBuffer.wrap(messageBytes), true);

        } catch (Exception e) {
            System.err.println("Error al enviar mensaje: " + e.getMessage());
        }
    }

    // --- Implementación de la Interfaz WebSocket.Listener (Manejo de Eventos) ---

    @Override
    public void onOpen(WebSocket webSocket) {
        System.out.println("\n[CONECTADO] Sesión WebSocket iniciada. ID: " + userId);
        this.webSocket = webSocket;
        // Suscríbete a un topic o envía un mensaje de LOGIN, si es necesario.
        
        // Simular envío de un mensaje
        sendChatMessage("¡Hola! Conectado y enviando el primer mensaje Protobuf binario.");
        // Simular envío de un segundo mensaje después de un tiempo
        try {
            Thread.sleep(1000); 
            sendChatMessage("Este es el segundo mensaje para verificar la conexión persistente.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
    }

    @Override
    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
        try {
            // 1. Deserializar los bytes recibidos a un objeto PaqueteDatos
            PaqueteDatos receivedData = PaqueteDatos.parseFrom(data.array());

            // 2. Procesar el mensaje
            System.out.println("\n[RECIBIDO]");
            System.out.println("Usuario: " + receivedData.getUsuarioId());
            System.out.println("Tipo: " + receivedData.getTipo());
            System.out.println("Contenido: " + receivedData.getContenido());
            
        } catch (IOException e) {
            System.err.println("Error al deserializar mensaje Protobuf: " + e.getMessage());
        }
        
        // Solicita más datos binarios si aún no ha terminado
        return null;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        // Maneja mensajes de texto (ej. si el servidor envía JSON o texto plano)
        System.out.println("[RECIBIDO TEXTO]: " + data);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        System.out.println("\n[DESCONECTADO] Código: " + statusCode + ", Razón: " + reason);
        this.webSocket = null;
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        System.err.println("\n[ERROR EN WS] " + error.getMessage());
    }

    public static void main(String[] args) throws InterruptedException {
        // Cambia esta URL por la de tu servidor Spring Boot con el endpoint WebSocket
        String wsUrl = "ws://localhost:8085/ws-chat"; 
        
        WebSocketChatClient client = new WebSocketChatClient();
        client.connect(wsUrl);

        // Mantiene el hilo principal vivo para que el hilo de WebSocket pueda continuar
        TimeUnit.SECONDS.sleep(10); 
    }
}
