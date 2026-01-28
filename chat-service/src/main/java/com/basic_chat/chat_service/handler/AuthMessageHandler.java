package com.basic_chat.chat_service.handler;

import java.io.IOException;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.UserPresenceEvent;
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
    private final RabbitTemplate rabbitTemplate;

    public AuthMessageHandler(JwtValidator jwtValidator, SessionManager sessionManager, RabbitTemplate rabbitTemplate) {
        this.jwtValidator = jwtValidator;
        this.sessionManager = sessionManager;
        this.rabbitTemplate = rabbitTemplate;
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
            // Validar token
            String token = authMessage.getToken();
            if (!isValidToken(token)) {
                log.warn("Token vacío recibido de sesión: {}", sessionId);
                sendAuthError(session, "Token requerido");
                return;
            }

            // Validar y extraer claims
            Claims claims = jwtValidator.validateToken(token);
            String userId = jwtValidator.getUserId(claims);
            String username = jwtValidator.getUsername(claims);

            // Registrar sesión y notificar
            registerSession(sessionId, userId, username, session);

            // Enviar respuesta exitosa
            sendAuthSuccess(session, userId, username);
            log.info("Usuario autenticado: {} ({})", username, userId);

        } catch (Exception e) {
            log.error("Error en autenticación: {}", e.getMessage());
            sendAuthError(session, "Token inválido o expirado");
            closeSession(session);
        }
    }

    /**
     * Valida que el token no sea nulo o vacío
     */
    private boolean isValidToken(String token) {
        return token != null && !token.isEmpty();
    }

    /**
     * Registra la sesión autenticada en SessionManager y publica evento de presencia
     */
    private void registerSession(String sessionId, String userId, String username, WebSocketSession session) {
        sessionManager.authenticateSession(sessionId, userId, username, session);
        rabbitTemplate.convertAndSend("user.presence",
            new UserPresenceEvent(userId, username, true, System.currentTimeMillis()));
    }

    /**
     * Envía una respuesta de autenticación exitosa
     */
    private void sendAuthSuccess(WebSocketSession session, String userId, String username) throws IOException {
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
    }

    /**
     * Envía una respuesta de error de autenticación
     */
    private void sendAuthError(WebSocketSession session, String errorMessage) {
        try {
            AuthResponse response = AuthResponse.newBuilder()
                .setSuccess(false)
                .setMessage(errorMessage)
                .build();

            WsMessage wsResponse = WsMessage.newBuilder()
                .setAuthResponse(response)
                .build();

            session.sendMessage(new BinaryMessage(wsResponse.toByteArray()));
        } catch (Exception e) {
            log.error("Error enviando mensaje de error: {}", e.getMessage());
        }
    }

    /**
     * Cierra la sesión WebSocket de forma segura
     */
    private void closeSession(WebSocketSession session) {
        try {
            session.close();
        } catch (IOException ex) {
            log.error("Error cerrando sesión", ex);
        }
    }

}
