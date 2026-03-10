package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;

import com.basic_chat.chat_service.models.PendingContactIdentity;
import com.basic_chat.chat_service.repository.PendingContactIdentityRepository;
import com.basic_chat.proto.MessagesProto;

import lombok.extern.slf4j.Slf4j;

/**
 * Handler offline para actualizaciones de identidad de contacto.
 * 
 * Cuando un contacto que ha compartido su identidad real está offline,
 * guarda la actualización de identidad como pendiente para ser notificado
 * cuando se conecte.
 * 
 * Este handler es necesario porque cuando un usuario descubre la identidad
 * real de un contacto (a través de un mensaje o interacción), esa información
 * debe llegar a todos los dispositivos del usuario. Si el usuario está offline,
 * la actualización de identidad se almacena como pendiente y se entrega
 * cuando consulta sus mensajes pendientes via REST API.
 * 
 * La arquitectura con connection-service como gateway WebSocket centralizado
 * requiere que chat-service maneje el almacenamiento y entrega de estas
 * actualizaciones de identidad de contacto.
 */
@Component
@Slf4j
public class OfflineContactIdentityHandler implements OfflineMessageHandler {

    private final PendingContactIdentityRepository pendingContactIdentityRepository;

    public OfflineContactIdentityHandler(PendingContactIdentityRepository pendingContactIdentityRepository) {
        this.pendingContactIdentityRepository = pendingContactIdentityRepository;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasContactIdentity();
    }

    @Override
    public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
        MessagesProto.ContactIdentity contactIdentity = message.getContactIdentity();
        
        PendingContactIdentity pendingIdentity = new PendingContactIdentity();
        pendingIdentity.setRecipient(recipient);
        pendingIdentity.setSenderId(contactIdentity.getSenderId());
        pendingIdentity.setSenderUsername(contactIdentity.getSenderUsername());
        
        pendingContactIdentityRepository.save(pendingIdentity);
        
        log.info("Identidad de contacto pendiente guardada - contacto: {}, identidad revelada a: {}", 
                contactIdentity.getSenderUsername(), recipient);
    }
}
