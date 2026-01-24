package com.basic_chat.chat_service.service;

import com.basic_chat.chat_service.models.PendingContactIdentity;
import com.basic_chat.chat_service.models.PendingReadReceipt;
import com.basic_chat.chat_service.models.PendingUnblock;
import com.basic_chat.chat_service.repository.PendingContactIdentityRepository;
import com.basic_chat.chat_service.repository.PendingUnblockRepository;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.ChatMessage;
import com.basic_chat.proto.MessagesProto.ContactIdentity;
import com.basic_chat.proto.MessagesProto.DeleteMessageRequest;
import com.basic_chat.proto.MessagesProto.MessagesReadUpdate;
import com.basic_chat.proto.MessagesProto.UnreadMessagesList;
import com.basic_chat.proto.MessagesProto.WsMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class PendingMessagesHandler {

    private final MessageService messageService;
    private final PendingUnblockRepository pendingUnblockRepository;
    private final PendingContactIdentityRepository pendingContactIdentityRepository;

    public PendingMessagesHandler(MessageService messageService,
                                  PendingUnblockRepository pendingUnblockRepository,
                                  PendingContactIdentityRepository pendingContactIdentityRepository) {
        this.messageService = messageService;
        this.pendingUnblockRepository = pendingUnblockRepository;
        this.pendingContactIdentityRepository = pendingContactIdentityRepository;
    }

    public void sendPendingMessages(WebSocketSession session, String username) {
        try {
            List<ChatMessage> messages = messageService.getUnreadMessages(username);
            if (messages.isEmpty()) {
                return;
            }

            UnreadMessagesList list = UnreadMessagesList.newBuilder()
                    .addAllMessages(messages)
                    .build();
            
            WsMessage wsMessage = WsMessage.newBuilder()
                    .setUnreadMessagesList(list)
                    .build();
            
            sendMessage(session, wsMessage);
            log.info("Mensajes pendientes enviados al usuario: {}", username);
        } catch (IOException e) {
            log.error("Error enviando mensajes pendientes al usuario: {}", username, e);
        }
    }

    public void sendPendingDeletions(WebSocketSession session, String username) {
        try {
            List<String> pendingDeletionIds = messageService.getAndClearPendingDeletions(username);

            for (String msgId : pendingDeletionIds) {
                DeleteMessageRequest request = DeleteMessageRequest.newBuilder()
                        .setMessageId(msgId)
                        .build();
                
                WsMessage wsMessage = WsMessage.newBuilder()
                        .setDeleteMessageRequest(request)
                        .build();
                
                sendMessage(session, wsMessage);
            }
            
            if (!pendingDeletionIds.isEmpty()) {
                log.info("Eliminaciones pendientes enviadas al usuario: {} (cantidad: {})", 
                        username, pendingDeletionIds.size());
            }
        } catch (IOException e) {
            log.error("Error enviando eliminaciones pendientes al usuario: {}", username, e);
        }
    }

    public void sendPendingUnblocks(WebSocketSession session, String username) {
        try {
            List<PendingUnblock> pendingUnblocks = pendingUnblockRepository.findByUnblockedUser(username);

            if (pendingUnblocks.isEmpty()) {
                return;
            }

            List<String> blockers = pendingUnblocks.stream()
                    .map(PendingUnblock::getBlocker)
                    .distinct()
                    .toList();

            MessagesProto.UnblockedUsersList list = MessagesProto.UnblockedUsersList.newBuilder()
                    .addAllUsers(blockers)
                    .build();

            WsMessage wsMessage = WsMessage.newBuilder()
                    .setUnblockedUsersList(list)
                    .build();

            sendMessage(session, wsMessage);
            pendingUnblockRepository.deleteAll(pendingUnblocks);
            
            log.info("Desbloqueos pendientes enviados al usuario: {} (cantidad: {})", 
                    username, blockers.size());
        } catch (IOException e) {
            log.error("Error enviando desbloqueos pendientes al usuario: {}", username, e);
        }
    }

    public void sendPendingReadReceipts(WebSocketSession session, String username) {
        try {
            List<PendingReadReceipt> pendingReceipts = messageService.getAndClearPendingReadReceipts(username);

            if (pendingReceipts.isEmpty()) {
                return;
            }

            Map<String, List<String>> receiptsByReader = pendingReceipts.stream()
                    .collect(Collectors.groupingBy(
                            PendingReadReceipt::getReader,
                            Collectors.mapping(PendingReadReceipt::getMessageId, Collectors.toList())
                    ));

            receiptsByReader.forEach((reader, ids) -> {
                try {
                    MessagesReadUpdate update = MessagesReadUpdate.newBuilder()
                            .addAllMessageIds(ids)
                            .setReaderUsername(reader)
                            .build();

                    WsMessage wsMessage = WsMessage.newBuilder()
                            .setMessagesReadUpdate(update)
                            .build();

                    sendMessage(session, wsMessage);
                    log.debug("Notificación de lectura enviada al usuario: {} (leído por: {})", username, reader);
                } catch (IOException e) {
                    log.error("Error enviando notificación de lectura al usuario: {}", username, e);
                }
            });
        } catch (Exception e) {
            log.error("Error procesando recibos de lectura pendientes para usuario: {}", username, e);
        }
    }

    public void sendPendingContactIdentities(WebSocketSession session, String username) {
        try {
            List<PendingContactIdentity> pendingIdentities = pendingContactIdentityRepository.findByRecipient(username);

            if (pendingIdentities.isEmpty()) {
                return;
            }

            for (PendingContactIdentity pending : pendingIdentities) {
                ContactIdentity identity = ContactIdentity.newBuilder()
                        .setSenderId(pending.getSenderId())
                        .setSenderUsername(pending.getSenderUsername())
                        .setContactUsername(username)
                        .build();

                WsMessage wsMessage = WsMessage.newBuilder()
                        .setContactIdentity(identity)
                        .build();

                sendMessage(session, wsMessage);
            }
            
            pendingContactIdentityRepository.deleteAll(pendingIdentities);
            log.info("Identidades de contacto pendientes enviadas al usuario: {} (cantidad: {})", 
                    username, pendingIdentities.size());
        } catch (IOException e) {
            log.error("Error enviando identidades de contacto pendientes al usuario: {}", username, e);
        }
    }

    private void sendMessage(WebSocketSession session, WsMessage wsMessage) throws IOException {
        session.sendMessage(new BinaryMessage(wsMessage.toByteArray()));
    }
}
