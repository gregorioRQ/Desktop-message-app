package com.basic_chat.chat_service.context;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.service.SessionManager;


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
