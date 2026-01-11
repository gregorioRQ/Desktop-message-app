package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.ClearHistoryRequest;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class ClearHistoryHandler implements WsMessageHandler{
    private final MessageService messageService;
    private final SessionManager sessionManager;

    public ClearHistoryHandler(MessageService messageService, SessionManager sessionManager) {
        this.messageService = messageService;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean supports(MessagesProto.WsMessage message) {
        return message.hasClearHistoryRequest();
    }

    @Override
    public void handle(SessionContext context, MessagesProto.WsMessage message) throws Exception {
        ClearHistoryRequest request = message.getClearHistoryRequest();
        
        // Obtener el usuario que solicita la eliminación (sender) desde la sesión
        String sender = sessionManager.getSessionInfo(context.getSession().getId()).getUsername();
        String recipient = request.getRecipient();

        log.info("Procesando solicitud de eliminación de historial entre {} y {}", sender, recipient);

        // Eliminar mensajes de la base de datos (ambas direcciones)
        messageService.deleteAllMessagesBetweenUsers(sender, recipient);

        // Reenviar la solicitud al destinatario si está en línea para que limpie su local
        SessionManager.SessionInfo recipientSession = sessionManager.findByUsername(recipient);

        if (sessionManager.isUserOnline(recipient) && recipientSession != null) {
            recipientSession.getWsSession()
                    .sendMessage(new BinaryMessage(message.toByteArray()));
            log.debug("Solicitud de clear_history reenviada a {}", recipient);
        }
    }
}
