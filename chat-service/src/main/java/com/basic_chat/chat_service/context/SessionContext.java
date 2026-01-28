package com.basic_chat.chat_service.context;

import java.io.IOException;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.service.SessionManager;


/**
 * Contexto de sesión que encapsula una conexión WebSocket y su estado de autenticación.
 * 
 * Esta clase proporciona una abstracción de alto nivel sobre las sesiones WebSocket,
 * permitiendo verificar el estado de autenticación, enviar mensajes binarios y gestionar
 * el ciclo de vida de la sesión (apertura y cierre). Actúa como intermediaria entre
 * los manejadores de WebSocket y el gestor de sesiones, proporcionando métodos convenientes
 * para las operaciones más comunes sobre una sesión de usuario autenticado.
 * 
 * Responsabilidades principales:
 * - Mantener referencia a la sesión WebSocket y su gestor asociado
 * - Verificar estado de autenticación (autenticado, pendiente de autenticación, expirado)
 * - Enviar mensajes binarios a través de la sesión WebSocket
 * - Cerrar la sesión y limpiar recursos asociados
 * 
 * @see SessionManager
 * @see WebSocketSession
 */
public class SessionContext {
    private WebSocketSession session;
    private SessionManager manager;

    public SessionContext(WebSocketSession session, SessionManager manager){
        this.session = session;
        this.manager =  manager;
    }   

    public String sessionId(){
        return session.getId();
    }

    public WebSocketSession getSession(){
        return this.session;
    }

    public boolean isAuthenticated(){
        return manager.isAuthenticated(session.getId());
    }

    public boolean isPendingAuthentication(){
        return manager.isPendingAuthentication(session.getId());
    }

    public boolean hasAuthenticationExpired(){
        return manager.hasAuthenticationExpired(session.getId());
    }

    public void close(CloseStatus status) throws IOException{
        session.close();
        manager.removeSession(session.getId());
        System.out.println("Sesion cerrada ID: " + session.getId());
    }

    public void send(BinaryMessage message) throws IOException{
        session.sendMessage(message);
    }

}
