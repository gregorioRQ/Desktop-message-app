package com.basic_chat.chat_service.handler;

import java.io.IOException;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.UserPresenceEvent;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.chat_service.service.RedisSessionService;
import com.basic_chat.proto.MessagesProto.AuthMessage;
import com.basic_chat.proto.MessagesProto.AuthResponse;
import com.basic_chat.proto.MessagesProto.WsMessage;

import lombok.extern.slf4j.Slf4j;

/**
 * Maneja mensajes de autenticación.
 * 
 * NOTA: El API Gateway es responsable de validar el JWT.
 * Este handler solo registra la sesión WebSocket localmente
 * usando el userId que proviene del mensaje de autenticación.
 */
@Component
@Slf4j
public class AuthMessageHandler implements WsMessageHandler{

    private final SessionManager sessionManager;
    private final RedisSessionService redisSessionService;
    private final RabbitTemplate rabbitTemplate;

    public AuthMessageHandler(SessionManager sessionManager, RedisSessionService redisSessionService, RabbitTemplate rabbitTemplate) {
        this.sessionManager = sessionManager;
        this.redisSessionService = redisSessionService;
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
            // Extraer userId y username del mensaje (ya validado por el gateway)
            String userId = authMessage.getUserId();
            String username = authMessage.getUsername();

            if (userId == null || userId.isEmpty() || username == null || username.isEmpty()) {
                log.warn("Mensaje de autenticación incompleto - sessionId: {}", sessionId);
                sendAuthError(session, "Datos de autenticación requeridos");
                return;
            }

            // Registrar la sesión WebSocket localmente
            sessionManager.registerSession(sessionId, userId, username, session);

            // Guardar sessionId en Redis (backup, normalmente lo hace el API Gateway)
            redisSessionService.saveSessionId(userId, sessionId);

            // Enviar respuesta de éxito
            sendAuthSuccess(session, userId, username);
            log.info("Sesión registrada - sessionId: {}, userId: {}, username: {}", sessionId, userId, username);
            
            // Publicar evento de presencia
            rabbitTemplate.convertAndSend("user.presence",
                new UserPresenceEvent(userId, username, true, System.currentTimeMillis()));

        } catch (Exception e) {
            log.error("Error registrando sesión: {}", e.getMessage());
            sendAuthError(session, "Error en registro de sesión");
            closeSession(session);
        }
    }

    /**
     * Envía una respuesta de autenticación exitosa
     */
    private void sendAuthSuccess(WebSocketSession session, String userId, String username) throws IOException {
        AuthResponse response = AuthResponse.newBuilder()
            .setSuccess(true)
            .setMessage("Sesión registrada exitosamente")
            .setUserId(userId)
            .setUsername(username)
            .build();

        WsMessage wsResponse = WsMessage.newBuilder()
            .setAuthResponse(response)
            .build();

        session.sendMessage(new BinaryMessage(wsResponse.toByteArray()));
    }

    /**
     * Envía una respuesta de error
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
            log.error("Error enviando respuesta de error: {}", e.getMessage());
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

