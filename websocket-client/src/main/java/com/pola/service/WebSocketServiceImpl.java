package com.pola.service;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.glassfish.tyrus.client.ClientManager;

import com.pola.config.WebSocketConfig;
import com.pola.proto.MessagesProto.AuthResponse;
import com.pola.proto.MessagesProto.WsMessage;

import jakarta.websocket.ClientEndpointConfig;
import jakarta.websocket.CloseReason;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;

/**
 * Implementación del servicio WebSocket
 * Principio SOLID: Single Responsibility - Solo maneja la comunicación WebSocket
 */

public class WebSocketServiceImpl extends Endpoint implements WebSocketService {
    private Session session;
    private final ClientManager client;
    private Consumer<WsMessage> messageListener;
    private Consumer<Boolean> connectionListener;
    private Consumer<Throwable> errorListener;
    private Consumer<String> authSuccessListener;
    
    public WebSocketServiceImpl() {
        this.client = ClientManager.createClient();
    }
    
    @Override
    public void connect(String token, String userId, String username) {
        try {
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .configurator(new ClientEndpointConfig.Configurator() {
                    @Override
                    public void beforeRequest(Map<String, List<String>> headers) {
                        if (token != null && !token.isEmpty()) {
                            // Agregar header Authorization con el JWT token para autenticación JWT en el gateway
                            System.out.println("Enviando token en WS Header (primeros 10 chars): " + token.substring(0, Math.min(token.length(), 10)) + "...");
                            headers.put("Authorization", java.util.Collections.singletonList("Bearer " + token.trim()));
                        }

                        // CRÍTICO: Agregar headers X-User-ID y X-Username requeridos por connection-service
                        // Estos headers son esenciales para que el servidor valide la conexión durante el handshake WebSocket
                        // sin ellos, connection-service cerrará la conexión con POLICY_VIOLATION
                        if (userId != null && !userId.isEmpty()) {
                            headers.put("X-User-ID", java.util.Collections.singletonList(userId));
                        } else {
                            throw new IllegalArgumentException("X-User-ID es obligatorio para la conexión WebSocket");
                        }

                        if (username != null && !username.isEmpty()) {
                            headers.put("X-Username", java.util.Collections.singletonList(username));
                        } else {
                            throw new IllegalArgumentException("X-Username es obligatorio para la conexión WebSocket");
                        }
                    }
                })
                .build();
            URI uri = new URI(WebSocketConfig.WS_URL);
            client.connectToServer(this, config, uri);
        } catch (Exception e) {
            notifyError(e);
        }
    }
    
    @Override
    public void disconnect() {
        // Cerrar la conexión WebSocket de manera ordenada
        if (session != null && session.isOpen()) {
            try {
                session.close();
                notifyConnectionChange(false);
            } catch (IOException e) {
                notifyError(e);
            }
        }
    }
    
    @Override
    public boolean isConnected() {
        // Verificar si la sesión WebSocket está activa y abierta
        return session != null && session.isOpen();
    }
    
    @Override
    public void sendMessage(WsMessage message) {
        if (!isConnected()) {
            throw new IllegalStateException("WebSocket no está conectado");
        }
        
        try {
            // Convertir mensaje Protobuf a bytes y enviar como mensaje binario WebSocket
            byte[] data = message.toByteArray();
            ByteBuffer buffer = ByteBuffer.wrap(data);
            session.getBasicRemote().sendBinary(buffer);
        } catch (IOException e) {
            notifyError(e);
        }
    }
    
    @Override
    public void onOpen(Session session, EndpointConfig config) {
        // CRÍTICO: Callback invocado cuando el handshake WebSocket se completa exitosamente
        // En este punto, connection-service ya ha validado los headers X-User-ID y X-Username
        this.session = session;
        System.out.println("Conexión WebSocket establecida: " + session.getId());
        notifyConnectionChange(true);
        
        // Registrar handler para procesar mensajes binarios recibidos desde el servidor
        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
                processMessage(message);
            }
        });
    }
    
    private void processMessage(ByteBuffer buffer) {
        try {
            // Deserializar mensaje binario Protobuf recibido del servidor
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            WsMessage message = WsMessage.parseFrom(data);
            
            // CRÍTICO: Verificar si es respuesta de autenticación del servidor
            // El servidor responde exitosamente si los headers X-User-ID y X-Username son válidos
            if(message.hasAuthResponse()){
                AuthResponse authResponse = message.getAuthResponse();
                if(authResponse.getSuccess()){
                    System.out.println("Autenticacion exitosa: " + authResponse.getUserId());
                    notifyConnectionChange(true);
                    if (authSuccessListener != null) {
                        authSuccessListener.accept(authResponse.getUserId());
                    }
                    
                }else{
                    System.err.println("Error de autenticacion: " + authResponse.getMessage());
                    notifyError(new Exception("Auth failed: " + authResponse.getMessage()));
                    disconnect();
                }
                return;
            }
   
            // Pasar cualquier otro mensaje al listener registrado
            if (messageListener != null) {
                messageListener.accept(message);
            }
        } catch (Exception e) {
            notifyError(e);
        }
    }
    
    @Override
    public void onClose(Session session, CloseReason closeReason) {
        // CRÍTICO: Callback invocado cuando el servidor cierra la conexión WebSocket
        // Puede ser por cierre normal o por POLICY_VIOLATION si faltan headers requeridos
        System.out.println("Conexión WebSocket cerrada: " + closeReason.getReasonPhrase());
        notifyConnectionChange(false);
    }
    
    @Override
    public void onError(Session session, Throwable throwable) {
        // CRÍTICO: Callback invocado cuando ocurre un error en la conexión WebSocket
        // Esto puede incluir errores de red, timeouts, o problemas de handshake
        System.err.println("Error en WebSocket: " + throwable.getMessage());
        notifyError(throwable);
    }
    
    @Override
    public void setMessageListener(Consumer<WsMessage> listener) {
        this.messageListener = listener;
    }
    
    @Override
    public void setConnectionListener(Consumer<Boolean> listener) {
        this.connectionListener = listener;
    }
    
    @Override
    public void setErrorListener(Consumer<Throwable> listener) {
        this.errorListener = listener;
    }
    
    @Override
    public void setAuthSuccessListener(Consumer<String> listener) {
        this.authSuccessListener = listener;
    }

    private void notifyConnectionChange(boolean connected) {
        if (connectionListener != null) {
            connectionListener.accept(connected);
        }
    }
    
    private void notifyError(Throwable error) {
        if (errorListener != null) {
            errorListener.accept(error);
        }
    }
}
