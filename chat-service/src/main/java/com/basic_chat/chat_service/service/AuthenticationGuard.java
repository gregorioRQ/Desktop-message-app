package com.basic_chat.chat_service.service;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import com.basic_chat.chat_service.context.SessionContext;

@Component
public class AuthenticationGuard {
    public void check(SessionContext context) throws IOException {
        if(context.isAuthenticated()) return;

        if(context.isPendingAuthentication()){
            if(context.hasAuthenticationExpired()){
                context.close(CloseStatus.POLICY_VIOLATION);
            }
            throw new SecurityException("Pendiente de autenticacion");
        }
        throw new SecurityException("No autenticado");
    }
}
