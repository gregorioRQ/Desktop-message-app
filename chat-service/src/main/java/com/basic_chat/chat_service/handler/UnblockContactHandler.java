package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.PendingUnblock;
import com.basic_chat.chat_service.repository.PendingUnblockRepository;
import com.basic_chat.chat_service.service.BlockService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.UnblockContactRequest;
import com.basic_chat.proto.MessagesProto.UnblockContactResponse;
import com.basic_chat.proto.MessagesProto.WsMessage;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class UnblockContactHandler implements WsMessageHandler {

    private final BlockService blockService;
    private final SessionManager sessionManager;
    private final PendingUnblockRepository pendingUnblockRepository;

    public UnblockContactHandler(BlockService blockService, SessionManager sessionManager, PendingUnblockRepository pendingUnblockRepository) {
        this.blockService = blockService;
        this.sessionManager = sessionManager;
        this.pendingUnblockRepository = pendingUnblockRepository;
    }

    @Override
    public boolean supports(WsMessage message) {
        return message.hasUnblockContactRequest();
    }

    @Override
    public void handle(SessionContext context, WsMessage message) throws Exception {
        UnblockContactRequest request = message.getUnblockContactRequest();
        String blocker = sessionManager.getSessionInfo(context.getSession().getId()).getUsername();
        String blocked = request.getRecipient();

        log.info("Usuario {} solicitando desbloquear a {}", blocker, blocked);

        boolean success = true;
        String responseMessage = "Usuario desbloqueado exitosamente";

        try {
            blockService.unblockUser(blocker, blocked);
            
            // Verificar si el usuario desbloqueado está en línea para notificarle
            if (sessionManager.isUserOnline(blocked)) {
                SessionManager.SessionInfo blockedSession = sessionManager.findByUsername(blocked);
                if (blockedSession != null) {
                    sendUnblockNotification(blockedSession.getWsSession(), blocker);
                    log.debug("Notificación de desbloqueo enviada a {}", blocked);
                }
            } else {
                // Si está offline, guardar pendiente
                PendingUnblock pending = new PendingUnblock(null, blocker, blocked, System.currentTimeMillis());
                pendingUnblockRepository.save(pending);
                log.info("Usuario {} offline. Desbloqueo pendiente guardado.", blocked);
            }
        } catch (Exception e) {
            log.error("Error al desbloquear usuario", e);
            success = false;
            responseMessage = "Error: " + e.getMessage();
        }

        UnblockContactResponse response = UnblockContactResponse.newBuilder()
                .setSuccess(success)
                .setMessage(responseMessage)
                .build();

        WsMessage wsResponse = WsMessage.newBuilder()
                .setUnblockContactResponse(response)
                .build();

        context.getSession().sendMessage(new BinaryMessage(wsResponse.toByteArray()));
    }

    private void sendUnblockNotification(org.springframework.web.socket.WebSocketSession session, String blocker) {
        try {
            MessagesProto.UnblockedUsersList list = MessagesProto.UnblockedUsersList.newBuilder()
                    .addUsers(blocker)
                    .build();

            WsMessage wsMessage = WsMessage.newBuilder()
                    .setUnblockedUsersList(list)
                    .build();
            session.sendMessage(new BinaryMessage(wsMessage.toByteArray()));
        } catch (Exception e) {
            log.error("Error enviando notificación de desbloqueo", e);
        }
    }
}
