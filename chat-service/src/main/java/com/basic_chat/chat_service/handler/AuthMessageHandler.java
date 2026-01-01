package com.basic_chat.chat_service.handler;

import java.io.IOException;
import java.util.Objects;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.security.JwtValidator;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto.AuthMessage;
import com.basic_chat.proto.MessagesProto.AuthResponse;
import com.basic_chat.proto.MessagesProto.WsMessage;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AuthMessageHandler implements WsMessageHandler{

    private final JwtValidator jwtValidator;
    private final SessionManager sessionManager;

    public AuthMessageHandler(JwtValidator jwtValidator, SessionManager sessionManager) {
        this.jwtValidator = jwtValidator;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean supports(WsMessage message) {
        return message.hasAuthMessage();
    }

    @Override
    public void handle(SessionContext context, WsMessage message) throws Exception {
         WebSocketSession session = context.getSession();
        AuthMessage authMessage = message.getAuthMessage();
        String sessionId = session.getId();

        try {
            String token = authMessage.getToken();

            if (token == null || token.isEmpty()) {
                log.warn("Token vacio recibido de sesion: {}", sessionId);
                sendAuthError(session, "Token requerido");
                return;
            }

            Claims claims = jwtValidator.validateToken(token);
            String userId = jwtValidator.getUserId(claims);
            String username = jwtValidator.getUsername(claims);
            
            // registrar sesión
            sessionManager.authenticateSession(sessionId, userId, username, session);

            AuthResponse response = AuthResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Autenticación exitosa")
                .setUserId(userId)
                .setUsername(username) 
                .build();

            WsMessage wsResponse = WsMessage.newBuilder()
                .setAuthResponse(response)
                .build();

            session.sendMessage(new BinaryMessage(wsResponse.toByteArray()));
            log.info("Usuario autenticado: {} ({})", username, userId);

        } catch (Exception e) {
            log.error("Error en autenticación: {}", e.getMessage());
            sendAuthError(session, "Token inválido o expirado");
            try {
                session.close();
            } catch (IOException ex) {
                log.error("Error cerrando sesión", ex);
            }
        }
    }

    private void sendAuthError(WebSocketSession session, String errorMessage) {
        try {
            AuthResponse response = AuthResponse.newBuilder()
                .setSuccess(false)
                .setMessage(errorMessage)
                .build();

            WsMessage wsResponse = WsMessage.newBuilder()
                .setAuthResponse(response)
                .build();
            
            session.sendMessage(new BinaryMessage(Objects.requireNonNull(wsResponse.toByteArray())));
        } catch (Exception e) {
            log.error("Error enviando mensaje de error: {}", e.getMessage());
        }
    }

}
