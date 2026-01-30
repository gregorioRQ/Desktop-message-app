package com.basic_chat.chat_service.handler;

import com.basic_chat.chat_service.models.PendingContactIdentity;
import com.basic_chat.chat_service.models.PendingReadReceipt;
import com.basic_chat.chat_service.models.PendingUnblock;
import com.basic_chat.chat_service.repository.PendingContactIdentityRepository;
import com.basic_chat.chat_service.repository.PendingUnblockRepository;
import com.basic_chat.chat_service.service.MessageService;
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

    /**
     * Envía los mensajes no leídos pendientes de un usuario a través de la sesión WebSocket.
     * Si no hay mensajes pendientes, el método retorna sin hacer nada.
     * 
     * @param session Sesión WebSocket del usuario
     * @param username Nombre de usuario propietario de los mensajes pendientes
     * @throws IOException Si ocurre un error al enviar el mensaje por WebSocket
     */
    public void sendPendingMessages(WebSocketSession session, String username) throws IOException {
        log.debug("Iniciando envío de mensajes pendientes para usuario: {}", username);
        
        try {
            List<ChatMessage> messages = messageService.getUnreadMessages(username);
            
            if (messages.isEmpty()) {
                log.debug("No hay mensajes pendientes para el usuario: {}", username);
                return;
            }

            log.info("Se encontraron {} mensajes pendientes para el usuario: {}", messages.size(), username);
            
            UnreadMessagesList list = UnreadMessagesList.newBuilder()
                    .addAllMessages(messages)
                    .build();
            
            WsMessage wsMessage = WsMessage.newBuilder()
                    .setUnreadMessagesList(list)
                    .build();
            
            sendMessage(session, wsMessage);
            log.info("Mensajes pendientes enviados exitosamente al usuario: {} (cantidad: {})", username, messages.size());
        } catch (IOException e) {
            log.error("Error enviando mensajes pendientes al usuario: {}", username, e);
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al procesar mensajes pendientes para usuario: {}", username, e);
            throw new IOException("Error al enviar mensajes pendientes", e);
        }
    }

    /**
     * Envía las eliminaciones de mensajes pendientes de un usuario a través de la sesión WebSocket.
     * Cada eliminación se envía como un mensaje individual. Después del envío exitoso,
     * los registros de eliminación pendiente se limpian.
     * 
     * @param session Sesión WebSocket del usuario
     * @param username Nombre de usuario propietario de las eliminaciones pendientes
     * @throws IOException Si ocurre un error al enviar los mensajes por WebSocket
     */
    public void sendPendingDeletions(WebSocketSession session, String username) throws IOException {
        log.debug("Iniciando envío de eliminaciones pendientes para usuario: {}", username);
        
        try {
            List<String> pendingDeletionIds = messageService.getAndClearPendingDeletions(username);

            if (pendingDeletionIds.isEmpty()) {
                log.debug("No hay eliminaciones pendientes para el usuario: {}", username);
                return;
            }
            
            log.info("Se encontraron {} eliminaciones pendientes para el usuario: {}", pendingDeletionIds.size(), username);

            for (String msgId : pendingDeletionIds) {
                log.debug("Enviando eliminación de mensaje: {} para usuario: {}", msgId, username);
                
                DeleteMessageRequest request = DeleteMessageRequest.newBuilder()
                        .setMessageId(msgId)
                        .build();
                
                WsMessage wsMessage = WsMessage.newBuilder()
                        .setDeleteMessageRequest(request)
                        .build();
                
                sendMessage(session, wsMessage);
            }
            
            log.info("Eliminaciones pendientes enviadas exitosamente al usuario: {} (cantidad: {})", 
                    username, pendingDeletionIds.size());
        } catch (IOException e) {
            log.error("Error enviando eliminaciones pendientes al usuario: {}", username, e);
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al procesar eliminaciones pendientes para usuario: {}", username, e);
            throw new IOException("Error al enviar eliminaciones pendientes", e);
        }
    }

    /**
     * Envía la lista de usuarios que han desbloqueado al usuario especificado.
     * Después del envío exitoso, los registros de desbloqueo pendiente se eliminan de la base de datos.
     * 
     * @param session Sesión WebSocket del usuario
     * @param username Nombre de usuario que recibirá las notificaciones de desbloqueo
     * @throws IOException Si ocurre un error al enviar el mensaje por WebSocket
     */
    public void sendPendingUnblocks(WebSocketSession session, String username) throws IOException {
        log.debug("Iniciando envío de desbloqueos pendientes para usuario: {}", username);
        
        try {
            List<PendingUnblock> pendingUnblocks = pendingUnblockRepository.findByUnblockedUser(username);

            if (pendingUnblocks.isEmpty()) {
                log.debug("No hay desbloqueos pendientes para el usuario: {}", username);
                return;
            }

            log.info("Se encontraron {} registros de desbloqueos pendientes para el usuario: {}", pendingUnblocks.size(), username);
            
            List<String> blockers = pendingUnblocks.stream()
                    .map(PendingUnblock::getBlocker)
                    .distinct()
                    .toList();
            
            log.debug("Usuarios que han desbloqueado al usuario {}: {}", username, blockers);

            MessagesProto.UnblockedUsersList list = MessagesProto.UnblockedUsersList.newBuilder()
                    .addAllUsers(blockers)
                    .build();

            WsMessage wsMessage = WsMessage.newBuilder()
                    .setUnblockedUsersList(list)
                    .build();

            sendMessage(session, wsMessage);
            pendingUnblockRepository.deleteAll(pendingUnblocks);
            
            log.info("Desbloqueos pendientes enviados exitosamente al usuario: {} (cantidad: {})", 
                    username, blockers.size());
        } catch (IOException e) {
            log.error("Error enviando desbloqueos pendientes al usuario: {}", username, e);
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al procesar desbloqueos pendientes para usuario: {}", username, e);
            throw new IOException("Error al enviar desbloqueos pendientes", e);
        }
    }

    /**
     * Envía las notificaciones de lectura pendientes de un usuario a través de la sesión WebSocket.
     * Agrupa los recibos de lectura por usuario lector y envía una notificación por cada lector.
     * Después del envío exitoso, los registros de recibo de lectura se limpian.
     * 
     * @param session Sesión WebSocket del usuario
     * @param username Nombre de usuario propietario de los recibos de lectura pendientes
     * @throws IOException Si ocurre un error al enviar los mensajes por WebSocket
     */
    public void sendPendingReadReceipts(WebSocketSession session, String username) throws IOException {
        log.debug("Iniciando envío de recibos de lectura pendientes para usuario: {}", username);
        
        try {
            List<PendingReadReceipt> pendingReceipts = messageService.getAndClearPendingReadReceipts(username);

            if (pendingReceipts.isEmpty()) {
                log.debug("No hay recibos de lectura pendientes para el usuario: {}", username);
                return;
            }

            log.info("Se encontraron {} recibos de lectura pendientes para el usuario: {}", pendingReceipts.size(), username);
            
            Map<String, List<String>> receiptsByReader = pendingReceipts.stream()
                    .collect(Collectors.groupingBy(
                            PendingReadReceipt::getReader,
                            Collectors.mapping(PendingReadReceipt::getMessageId, Collectors.toList())
                    ));

            log.debug("Agrupados en {} lectores diferentes para el usuario: {}", receiptsByReader.size(), username);
            
            receiptsByReader.forEach((reader, ids) -> {
                try {
                    log.debug("Enviando {} notificaciones de lectura de usuario {} al usuario {}", ids.size(), reader, username);
                    
                    MessagesReadUpdate update = MessagesReadUpdate.newBuilder()
                            .addAllMessageIds(ids)
                            .setReaderUsername(reader)
                            .build();

                    WsMessage wsMessage = WsMessage.newBuilder()
                            .setMessagesReadUpdate(update)
                            .build();

                    sendMessage(session, wsMessage);
                    log.debug("Notificación de lectura enviada exitosamente al usuario: {} (leído por: {})", username, reader);
                } catch (IOException e) {
                    log.error("Error enviando notificación de lectura al usuario: {} desde lector: {}", username, reader, e);
                    throw new RuntimeException(e);
                }
            });
            
            log.info("Recibos de lectura pendientes enviados exitosamente al usuario: {} (cantidad de lectores: {})", username, receiptsByReader.size());
        } catch (RuntimeException e) {
            log.error("Error al procesar recibos de lectura pendientes para usuario: {}", username, e);
            throw new IOException("Error al enviar recibos de lectura pendientes", e);
        } catch (Exception e) {
            log.error("Error inesperado al procesar recibos de lectura pendientes para usuario: {}", username, e);
            throw new IOException("Error al enviar recibos de lectura pendientes", e);
        }
    }

    /**
     * Envía las identidades de contacto pendientes de un usuario a través de la sesión WebSocket.
     * Cada identidad de contacto se envía como un mensaje individual. Después del envío exitoso,
     * los registros de identidad de contacto pendiente se eliminan de la base de datos.
     * 
     * @param session Sesión WebSocket del usuario
     * @param username Nombre de usuario que recibirá las identidades de contacto pendientes
     * @throws IOException Si ocurre un error al enviar los mensajes por WebSocket
     */
    public void sendPendingContactIdentities(WebSocketSession session, String username) throws IOException {
        log.debug("Iniciando envío de identidades de contacto pendientes para usuario: {}", username);
        
        try {
            List<PendingContactIdentity> pendingIdentities = pendingContactIdentityRepository.findByRecipient(username);

            if (pendingIdentities.isEmpty()) {
                log.debug("No hay identidades de contacto pendientes para el usuario: {}", username);
                return;
            }

            log.info("Se encontraron {} identidades de contacto pendientes para el usuario: {}", pendingIdentities.size(), username);
            
            for (PendingContactIdentity pending : pendingIdentities) {
                log.debug("Enviando identidad de contacto de {} al usuario {}", pending.getSenderUsername(), username);
                
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
            log.info("Identidades de contacto pendientes enviadas exitosamente al usuario: {} (cantidad: {})", 
                    username, pendingIdentities.size());
        } catch (IOException e) {
            log.error("Error enviando identidades de contacto pendientes al usuario: {}", username, e);
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al procesar identidades de contacto pendientes para usuario: {}", username, e);
            throw new IOException("Error al enviar identidades de contacto pendientes", e);
        }
    }

    /**
     * Envía un mensaje protobuf a través de la sesión WebSocket en formato binario.
     * Este es un método privado utilizado internamente para enviar todas las notificaciones pendientes.
     * 
     * @param session Sesión WebSocket donde se enviará el mensaje
     * @param wsMessage Mensaje protobuf a enviar
     * @throws IOException Si ocurre un error al enviar el mensaje por la sesión WebSocket
     */
    private void sendMessage(WebSocketSession session, WsMessage wsMessage) throws IOException {
        try {
            session.sendMessage(new BinaryMessage(wsMessage.toByteArray()));
            log.trace("Mensaje binario enviado exitosamente a través de WebSocket");
        } catch (IOException e) {
            log.error("Error al enviar mensaje binario a través de WebSocket", e);
            throw e;
        }
    }
}
