package com.pola.service;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.function.Consumer;

import org.glassfish.tyrus.client.ClientManager;

import com.pola.config.WebSocketConfig;
import com.pola.proto.MessagesProto.AuthResponse;
import com.pola.proto.MessagesProto.WsMessage;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;

/**
 * Implementación del servicio WebSocket
 * Principio SOLID: Single Responsibility - Solo maneja la comunicación WebSocket
 */

@ClientEndpoint
public class WebSocketServiceImpl implements WebSocketService{
    private Session session;
    private final ClientManager client;
    private Consumer<WsMessage> messageListener;
    private Consumer<Boolean> connectionListener;
    private Consumer<Throwable> errorListener;
    
    public WebSocketServiceImpl() {
        this.client = ClientManager.createClient();
    }
    
    @Override
    public void connect() {
        try {
            URI uri = new URI(WebSocketConfig.WS_URL);
            session = client.connectToServer(this, uri);
            notifyConnectionChange(true);
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
    
    @OnOpen
    public void onOpen(Session session) {
        System.out.println("Conexión WebSocket establecida: " + session.getId());
    }
    
    @OnMessage
    public void onMessage(ByteBuffer buffer) {
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
    
    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        System.out.println("Conexión WebSocket cerrada: " + closeReason.getReasonPhrase());
        notifyConnectionChange(false);
    }
    
    @OnError
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
