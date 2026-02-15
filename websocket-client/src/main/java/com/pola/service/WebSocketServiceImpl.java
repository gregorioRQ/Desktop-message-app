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
    public void connect(String token) {
        try {
            ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .configurator(new ClientEndpointConfig.Configurator() {
                    @Override
                    public void beforeRequest(Map<String, List<String>> headers) {
                        if (token != null && !token.isEmpty()) {
                            // DEBUG: Verificar contenido del token (Payload)
                            try {
                                String[] parts = token.split("\\.");
                                if (parts.length > 1) {
                                    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                                    System.out.println("[DEBUG] Payload del Token enviado: " + payload);
                                }
                            } catch (Exception e) {
                                System.out.println("[DEBUG] No se pudo decodificar el payload del token: " + e.getMessage());
                            }

                            // Trim para evitar espacios accidentales y log para depuración
                            System.out.println("Enviando token en WS Header (primeros 10 chars): " + token.substring(0, Math.min(token.length(), 10)) + "...");
                            headers.put("Authorization", java.util.Collections.singletonList("Bearer " + token.trim()));
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
        return session != null && session.isOpen();
    }
    
    @Override
    public void sendMessage(WsMessage message) {
        if (!isConnected()) {
            throw new IllegalStateException("WebSocket no está conectado");
        }
        
        try {
            byte[] data = message.toByteArray();
            ByteBuffer buffer = ByteBuffer.wrap(data);
            session.getBasicRemote().sendBinary(buffer);
        } catch (IOException e) {
            notifyError(e);
        }
    }
    
    @Override
    public void onOpen(Session session, EndpointConfig config) {
        this.session = session;
        System.out.println("Conexión WebSocket establecida: " + session.getId());
        notifyConnectionChange(true);
        
        session.addMessageHandler(new MessageHandler.Whole<ByteBuffer>() {
            @Override
            public void onMessage(ByteBuffer message) {
                processMessage(message);
            }
        });
    }
    
    private void processMessage(ByteBuffer buffer) {
        try {
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            WsMessage message = WsMessage.parseFrom(data);
            
            // Verificar si es respuesta de autenticacion
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
   
            if (messageListener != null) {
                messageListener.accept(message);
            }
        } catch (Exception e) {
            notifyError(e);
        }
    }
    
    @Override
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("Conexión WebSocket cerrada: " + closeReason.getReasonPhrase());
        notifyConnectionChange(false);
    }
    
    @Override
    public void onError(Session session, Throwable throwable) {
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
