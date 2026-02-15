package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.PendingContactIdentity;
import com.basic_chat.chat_service.repository.PendingContactIdentityRepository;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.chat_service.service.RedisSessionService;
import com.basic_chat.proto.MessagesProto.ContactIdentity;
import com.basic_chat.proto.MessagesProto.WsMessage;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ContactIdentityHandler implements WsMessageHandler {

    private final SessionManager sessionManager;
    private final RedisSessionService redisSessionService;
    private final PendingContactIdentityRepository pendingContactIdentityRepository;

    public ContactIdentityHandler(SessionManager sessionManager, RedisSessionService redisSessionService, PendingContactIdentityRepository pendingContactIdentityRepository) {
        this.sessionManager = sessionManager;
        this.redisSessionService = redisSessionService;
        this.pendingContactIdentityRepository = pendingContactIdentityRepository;
    }

    @Override
    public boolean supports(WsMessage message) {
        return message.hasContactIdentity();
    }

    /**
     * Procesa una solicitud de identidad de contacto.
     * 
     * Flujo:
     * 1. Extrae los datos de la identidad (recipient, senderId, senderUsername)
     * 2. Valida que todos los datos requeridos estén presentes
     * 3. Si el destinatario está online: entrega el mensaje en tiempo real
     * 4. Si el destinatario está offline: guarda la identidad como pendiente en BD
     * 5. Registra eventos con logs en diferentes niveles
     * 
     * @param context contexto de la sesión WebSocket del remitente
     * @param message mensaje protobuf que contiene la identidad del contacto
     * @throws Exception si ocurre un error crítico durante el procesamiento
     */
    @Override
    public void handle(SessionContext context, WsMessage message) throws Exception {
        ContactIdentity identity = message.getContactIdentity();
        String recipient = null;
        String senderId = null;
        String senderUsername = null;

        try {
            // Extraer datos del mensaje
            recipient = identity.getContactUsername();
            senderId = identity.getSenderId();
            senderUsername = identity.getSenderUsername();

            // Validar que la sesión del contexto existe
            if (context == null || context.getSession() == null) {
                log.error("Contexto o sesión nula al procesar ContactIdentity");
                return;
            }

            // Validar que todos los datos requeridos estén presentes
            if (!isValidContactIdentity(recipient, senderId, senderUsername)) {
                log.warn("ContactIdentity inválido - recipient: {}, senderId: {}, senderUsername: {}", 
                        recipient, senderId, senderUsername);
                return;
            }

            log.info("Procesando ContactIdentity de {} (ID: {}) para {}", 
                    senderUsername, senderId, recipient);

            // Intentar entregar en tiempo real si el usuario está online
            if (redisSessionService.isUserOnlineByUsername(recipient)) {
                deliverContactIdentityRealtime(recipient, message);
            } else {
                // Si está offline, guardar como pendiente para entrega posterior
                savePendingContactIdentity(recipient, senderId, senderUsername);
            }

        } catch (Exception e) {
            log.error("Error inesperado al procesar ContactIdentity - recipient: {}, senderId: {}, senderUsername: {}", 
                    recipient, senderId, senderUsername, e);
        }
    }

    /**
     * Entrega la identidad del contacto en tiempo real al usuario online.
     * 
     * Flujo:
     * 1. Busca la sesión WebSocket del destinatario
     * 2. Valida que la sesión existe y está abierta
     * 3. Envía el mensaje a través de WebSocket
     * 4. Registra el resultado en logs
     * 
     * @param recipient nombre del usuario destinatario
     * @param message mensaje protobuf con la identidad
     */
    private void deliverContactIdentityRealtime(String recipient, WsMessage message) {
        try {
            // Buscar la sesión del destinatario
            String sessionId = redisSessionService.getSessionIdByUsername(recipient);
            if (sessionId == null) {
                log.warn("SessionId no encontrado para usuario online: {}", recipient);
                // Guardar como pendiente en caso de que haya desaparecido
                ContactIdentity identity = message.getContactIdentity();
                savePendingContactIdentity(recipient, identity.getSenderId(), identity.getSenderUsername());
                return;
            }
            SessionManager.SessionInfo recipientSession = sessionManager.getSessionInfo(sessionId);

            if (recipientSession == null) {
                log.warn("Sesión no encontrada para usuario online: {}", recipient);
                // Guardar como pendiente en caso de que la sesión haya desaparecido
                ContactIdentity identity = message.getContactIdentity();
                savePendingContactIdentity(recipient, identity.getSenderId(), identity.getSenderUsername());
                return;
            }

            // Validar que la sesión WebSocket existe y está abierta
            WebSocketSession wsSession = recipientSession.getWsSession();
            if (wsSession == null || !wsSession.isOpen()) {
                log.warn("Sesión WebSocket no disponible o cerrada para usuario: {}", recipient);
                // Guardar como pendiente en caso de que la sesión se haya cerrado
                ContactIdentity identity = message.getContactIdentity();
                savePendingContactIdentity(recipient, identity.getSenderId(), identity.getSenderUsername());
                return;
            }

            // Enviar el mensaje
            wsSession.sendMessage(new BinaryMessage(message.toByteArray()));
            log.info("ContactIdentity entregado en tiempo real a {}", recipient);

        } catch (Exception e) {
            log.error("Error entregando ContactIdentity en tiempo real al usuario: {}", recipient, e);
            // Guardar como pendiente en caso de error al enviar
            try {
                ContactIdentity identity = message.getContactIdentity();
                savePendingContactIdentity(recipient, identity.getSenderId(), identity.getSenderUsername());
            } catch (Exception fallbackError) {
                log.error("Error en fallback al guardar ContactIdentity pendiente para: {}", recipient, fallbackError);
            }
        }
    }

    /**
     * Guarda una identidad de contacto como pendiente en la base de datos.
     * 
     * Se utiliza cuando el usuario destinatario está offline. La identidad
     * será entregada cuando el usuario se conecte nuevamente.
     * 
     * @param recipient nombre del usuario destinatario
     * @param senderId ID único del usuario remitente
     * @param senderUsername nombre de usuario del remitente
     */
    private void savePendingContactIdentity(String recipient, String senderId, String senderUsername) {
        try {
            // Validar datos antes de guardar
            if (!isValidContactIdentity(recipient, senderId, senderUsername)) {
                log.warn("No se puede guardar ContactIdentity pendiente - datos inválidos");
                return;
            }

            // Crear la entidad pendiente
            PendingContactIdentity pending = new PendingContactIdentity(
                    null,  // ID generado por BD
                    recipient,
                    senderId,
                    senderUsername
            );

            // Guardar en base de datos
            pendingContactIdentityRepository.save(pending);
            log.info("ContactIdentity guardado como pendiente - destinatario: {}, remitente: {} (ID: {})", 
                    recipient, senderUsername, senderId);

        } catch (Exception e) {
            log.error("Error al guardar ContactIdentity pendiente en BD - recipient: {}, senderId: {}, senderUsername: {}", 
                    recipient, senderId, senderUsername, e);
        }
    }

    /**
     * Valida que una identidad de contacto contenga todos los datos requeridos.
     * 
     * @param recipient nombre del usuario destinatario
     * @param senderId ID único del usuario remitente
     * @param senderUsername nombre de usuario del remitente
     * @return true si todos los datos son válidos, false en caso contrario
     */
    private boolean isValidContactIdentity(String recipient, String senderId, String senderUsername) {
        // Validar recipient
        if (recipient == null || recipient.trim().isEmpty()) {
            log.debug("Recipient es nulo o vacío");
            return false;
        }

        // Validar senderId
        if (senderId == null || senderId.trim().isEmpty()) {
            log.debug("SenderId es nulo o vacío");
            return false;
        }

        // Validar senderUsername
        if (senderUsername == null || senderUsername.trim().isEmpty()) {
            log.debug("SenderUsername es nulo o vacío");
            return false;
        }

        // Validar que el usuario no envíe su propia identidad
        if (recipient.equalsIgnoreCase(senderUsername)) {
            log.debug("Usuario intenta enviar su propia identidad - usuario: {}", recipient);
            return false;
        }

        return true;
    }
}
