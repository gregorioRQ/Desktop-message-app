package com.basic_chat.chat_service.service;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;

import com.basic_chat.chat_service.context.SessionContext;

/**
 * @deprecated Esta clase ya no es necesaria.
 * La validación de autenticación es responsabilidad del API Gateway.
 * Se mantiene solo para compatibilidad hacia atrás.
 */
@Deprecated(forRemoval = true)
@Component
public class AuthenticationGuard {
    public void check(SessionContext context) throws IOException {
        // Esta clase ya no realiza validación de autenticación
        // El API Gateway es responsable de ello
    }
}
