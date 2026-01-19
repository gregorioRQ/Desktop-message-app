package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.PendingContactIdentity;
import com.basic_chat.chat_service.repository.PendingContactIdentityRepository;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto.ContactIdentity;
import com.basic_chat.proto.MessagesProto.WsMessage;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ContactIdentityHandler implements WsMessageHandler {

    private final SessionManager sessionManager;
    private final PendingContactIdentityRepository pendingContactIdentityRepository;

    public ContactIdentityHandler(SessionManager sessionManager, PendingContactIdentityRepository pendingContactIdentityRepository) {
        this.sessionManager = sessionManager;
        this.pendingContactIdentityRepository = pendingContactIdentityRepository;
    }

    @Override
    public boolean supports(WsMessage message) {
        return message.hasContactIdentity();
    }

    @Override
    public void handle(SessionContext context, WsMessage message) throws Exception {
        ContactIdentity identity = message.getContactIdentity();
        // Asumimos que contact_username es el destinatario al que se le envía la identidad
        String recipient = identity.getContactUsername();

        log.info("Procesando ContactIdentity de {} para {}", identity.getSenderUsername(), recipient);

        if (sessionManager.isUserOnline(recipient)) {
            SessionManager.SessionInfo recipientSession = sessionManager.findByUsername(recipient);
            if (recipientSession != null) {
                recipientSession.getWsSession().sendMessage(new BinaryMessage(message.toByteArray()));
                log.debug("ContactIdentity enviado a {}", recipient);
                return;
            }
        }

        // Si el usuario está offline, guardamos la solicitud pendiente
        PendingContactIdentity pending = new PendingContactIdentity(
            null, 
            recipient, 
            identity.getSenderId(), 
            identity.getSenderUsername()
        );
        pendingContactIdentityRepository.save(pending);
        log.info("Usuario {} offline. ContactIdentity guardado como pendiente.", recipient);
    }
}
