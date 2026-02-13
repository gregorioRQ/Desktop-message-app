package com.basic_chat.chat_service.context;

import java.io.IOException;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.service.SessionManager;


/**
 * Contexto de sesión que encapsula una conexión WebSocket y proporciona acceso a datos del usuario.
 * 
 * Esta clase proporciona una abstracción de alto nivel sobre las sesiones WebSocket,
 * permitiendo obtener información del usuario, enviar mensajes binarios y gestionar
 * el ciclo de vida de la sesión. Actúa como intermediaria entre los manejadores de WebSocket 
 * y el gestor de sesiones.
 * 
 * NOTA: La autenticación es responsabilidad del API Gateway. Esta clase asume que 
 * la sesión ya está autenticada cuando se crea el contexto.
 * 
 * @see SessionManager
 * @see WebSocketSession
 */
public class SessionContext {
    private WebSocketSession session;
    private SessionManager manager;

    public SessionContext(WebSocketSession session, SessionManager manager){
        this.session = session;
        this.manager = manager;
    }   

    public String sessionId(){
        return session.getId();
    }

    public WebSocketSession getSession(){
        return this.session;
    }

    public void close(CloseStatus status) throws IOException{
        session.close();
        manager.removeSession(session.getId());
    }

    public void send(BinaryMessage message) throws IOException{
        session.sendMessage(message);
    }

}

