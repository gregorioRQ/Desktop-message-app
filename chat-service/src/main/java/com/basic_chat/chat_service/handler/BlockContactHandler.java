package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.service.BlockService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.BlockContactRequest;
import com.basic_chat.proto.MessagesProto.BlockContactResponse;
import com.basic_chat.proto.MessagesProto.WsMessage;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BlockContactHandler implements WsMessageHandler{
    private final BlockService blockService;
    private final SessionManager sessionManager;

    public BlockContactHandler(BlockService blockService, SessionManager sessionManager) {
        this.blockService = blockService;
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean supports(WsMessage message) {
        return message.hasBlockContactRequest();
    }

    @Override
    public void handle(SessionContext context, WsMessage message) throws Exception {
        BlockContactRequest request = message.getBlockContactRequest();
        String blocker = sessionManager.getSessionInfo(context.getSession().getId()).getUsername();
        String blocked = request.getRecipient(); 

        log.info("Usuario {} solicitando bloquear a {}", blocker, blocked);

        boolean success = true;
        String responseMessage = "Usuario bloqueado exitosamente";

        try {
            blockService.blockUser(blocker, blocked);
            
            // Notificar al usuario bloqueado si está en línea
            if (sessionManager.isUserOnline(blocked)) {
                SessionManager.SessionInfo blockedSession = sessionManager.findByUsername(blocked);
                if (blockedSession != null) {
                    sendBlockNotification(blockedSession.getWsSession(), blocker);
                    log.debug("Notificación de bloqueo enviada a {}", blocked);
                }
            }
        } catch (Exception e) {
            log.error("Error al bloquear usuario", e);
            success = false;
            responseMessage = "Error: " + e.getMessage();
        }

        BlockContactResponse response = BlockContactResponse.newBuilder()
                .setSuccess(success)
                .setMessage(responseMessage)
                .build();

        WsMessage wsResponse = WsMessage.newBuilder()
                .setBlockContactResponse(response)
                .build();

        context.getSession().sendMessage(new BinaryMessage(wsResponse.toByteArray()));
    }

    private void sendBlockNotification(org.springframework.web.socket.WebSocketSession session, String blocker) {
        try {
            MessagesProto.BlockedUsersList list = MessagesProto.BlockedUsersList.newBuilder()
                    .addUsers(blocker)
                    .build();

            WsMessage wsMessage = WsMessage.newBuilder()
                    .setBlockedUsersList(list)
                    .build();
            session.sendMessage(new BinaryMessage(wsMessage.toByteArray()));
        } catch (Exception e) {
            log.error("Error enviando notificación de bloqueo", e);
        }
    }
}
