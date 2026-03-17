package com.basic_chat.chat_service.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.springframework.stereotype.Service;

import com.basic_chat.chat_service.models.Message;
import com.basic_chat.chat_service.models.PendingBlock;
import com.basic_chat.chat_service.models.PendingClearHistory;
import com.basic_chat.chat_service.models.PendingContactIdentity;
import com.basic_chat.chat_service.models.PendingDeletion;
import com.basic_chat.chat_service.models.PendingReadReceipt;
import com.basic_chat.chat_service.models.PendingUnblock;
import com.basic_chat.chat_service.repository.MessageRepository;
import com.basic_chat.chat_service.repository.PendingBlockRepository;
import com.basic_chat.chat_service.repository.PendingClearHistoryRepository;
import com.basic_chat.chat_service.repository.PendingContactIdentityRepository;
import com.basic_chat.chat_service.repository.PendingDeletionRepository;
import com.basic_chat.chat_service.repository.PendingReadReceiptRepository;
import com.basic_chat.chat_service.repository.PendingUnblockRepository;
import com.basic_chat.chat_service.validator.MessageValidator;
import com.basic_chat.proto.MessagesProto;
import com.basic_chat.proto.MessagesProto.ChatMessage;
import com.basic_chat.proto.MessagesProto.DeleteMessageRequest;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final PendingDeletionRepository pendingDeletionRepository;
    private final PendingReadReceiptRepository pendingReadReceiptRepository;
    private final PendingBlockRepository pendingBlockRepository;
    private final PendingUnblockRepository pendingUnblockRepository;
    private final PendingClearHistoryRepository pendingClearHistoryRepository;
    private final PendingContactIdentityRepository pendingContactIdentityRepository;
    private final MessageValidator messageValidator;

    public MessageService(
            MessageRepository messageRepository,
            PendingDeletionRepository pendingDeletionRepository,
            PendingReadReceiptRepository pendingReadReceiptRepository,
            PendingBlockRepository pendingBlockRepository,
            PendingUnblockRepository pendingUnblockRepository,
            PendingClearHistoryRepository pendingClearHistoryRepository,
            PendingContactIdentityRepository pendingContactIdentityRepository,
            MessageValidator messageValidator) {
        this.messageRepository = messageRepository;
        this.pendingDeletionRepository = pendingDeletionRepository;
        this.pendingReadReceiptRepository = pendingReadReceiptRepository;
        this.pendingBlockRepository = pendingBlockRepository;
        this.pendingUnblockRepository = pendingUnblockRepository;
        this.pendingClearHistoryRepository = pendingClearHistoryRepository;
        this.pendingContactIdentityRepository = pendingContactIdentityRepository;
        this.messageValidator = messageValidator;
    }

    @Transactional
    public void saveMessage(MessagesProto.ChatMessage message) {
        validateMessage(message);
        try{
            Message mappedMessage = mapProtobufToEntity(message);
            log.debug("Guardando el mensaje: {}", mappedMessage.getId());
            messageRepository.save(mappedMessage);
            log.info("Mensaje guardado messageId: {}", mappedMessage.getId());
        }catch(Exception ex){
            log.error("Error al guardar el mensaje {}", message.getId());
            throw new RuntimeException("Error al guardar el mensaje", ex);
        }
        
    }

    private void validateMessage(MessagesProto.ChatMessage message){
        if(message == null){
            throw new IllegalArgumentException("El mensaje no puede ser nulo");
        }
        messageValidator.validate(message);
    }

    private Message mapProtobufToEntity(MessagesProto.ChatMessage protoMessage) {
        Message message = new Message();
        System.out.println(protoMessage.getId());
        message.setId(Long.parseLong(protoMessage.getId()));
        message.setFromUserId(protoMessage.getSender());
        message.setToUserId(protoMessage.getRecipient());
        message.setData(protoMessage.toByteArray());
        message.setSeen(false);

        LocalDateTime dateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(System.currentTimeMillis()),
                ZoneId.systemDefault()
        );
        message.setTimestamp(dateTime);
        message.setCreationTime(protoMessage.getTimestamp());
        
        return message;
    }

    @Transactional
    public List<ChatMessage> getUnreadMessages(String username) {
        if(username == null){
            log.warn("Parametros invalidos el username del usuario es nulo");
            return new ArrayList<>();
        }

        try{
            List<Message> messages = messageRepository.findByToUserIdAndSeenFalse(username);
            if (messages.isEmpty() || messages == null) {
                log.info("No hay mensajes para este usuario {}", username);
                return new ArrayList<>();
            }else{
                log.debug("Deserializando {} mensajes totales", messages.size());
                return messages.stream().map(m -> {
                try{
                    return ChatMessage.parseFrom(m.getData());
                }catch(Exception ex){
                    throw new RuntimeException("Error al deserializar el mensaje: " + m.getId());
                }}).toList();
                
            }
        }catch(Exception ex){
            log.error("Erro al procesar la lista de mensajes si leer para {}: {}", username, ex.getMessage());
            throw new RuntimeException("No se pudo procesar la lista de mensajes sin leer", ex);
        }
        
    }


    @Transactional
    public List<Message> markMessagesAsRead(List<String> messageIds, String reader) {
        if (messageIds == null || messageIds.isEmpty()) {
            log.warn("Lista de IDs vacia o nula {}", messageIds);
            return new ArrayList<>();
        }

        try {
            List<Long> ids = messageIds.stream().map(Long::valueOf).toList();
            log.debug("Buscando {} mensajes en la base de datos", ids.size());
            
            List<Message> messages = messageRepository.findAllById(ids);
            log.debug("Se encontraron {} mensajes", messages.size());
            
            List<Message> updatedMessages = new ArrayList<>();

            for (Message m : messages) {
                if (m.getToUserId().equals(reader) && !m.isSeen()) {
                    m.setSeen(true);
                    updatedMessages.add(m);
                }
            }
            
            if (!updatedMessages.isEmpty()) {
                messageRepository.saveAll(updatedMessages);
                log.info("{} mensajes marcados como leídos para el usuario {}", updatedMessages.size(), reader);
            }
            
            return updatedMessages;
            
        } catch (NumberFormatException ex) {
            log.error("Error al convertir los IDs de mensaje a números: {}", messageIds, ex);
            throw new IllegalArgumentException("IDs de mensaje inválidos", ex);
            
        } catch (Exception ex) {
            log.error("Error al acceder a la base de datos al marcar mensajes como leídos: {}", ex.getMessage(), ex);
            throw new RuntimeException("Error al marcar mensajes como leídos", ex);
        }
    }

    @Transactional
    public Message deleteMessage(DeleteMessageRequest request) {
        try {
            // Validar que la solicitud no sea nula
            if (request == null) {
                throw new IllegalArgumentException("DeleteMessageRequest no puede ser nulo");
            }
            
            // Validar que el messageId no esté vacío
            if (request.getMessageId() == null || request.getMessageId().isEmpty()) {
                throw new IllegalArgumentException("El ID del mensaje no puede estar vacío");
            }
            
            Long messageId = Long.valueOf(request.getMessageId());
            System.out.println("Intentando eliminar mensaje con ID: " + messageId);
            
            // Validar y obtener el mensaje
            Message message = messageValidator.validateAndGetMessage(messageId);
            messageValidator.validateMessageId(message);
            
            // Eliminar el mensaje
            messageRepository.deleteById(message.getId());
            System.out.println("Mensaje eliminado exitosamente. ID: " + messageId);
            
            return message;
            
        } catch (NumberFormatException ex) {
            String errorMsg = "El ID del mensaje no es un número válido: " + request.getMessageId();
            System.err.println(errorMsg);
            throw new IllegalArgumentException(errorMsg, ex);
        
        } catch (IllegalArgumentException | jakarta.persistence.EntityNotFoundException ex) {
            System.err.println("Validación fallida: " + ex.getMessage());
            throw ex;
            
        } catch (Exception ex) {
            String errorMsg = "Error inesperado al eliminar el mensaje con ID: " + request.getMessageId();
            System.err.println(errorMsg + " - " + ex.getMessage());
            throw new RuntimeException(errorMsg, ex);
        }
    }


    @Transactional
    public void deleteAllMessagesBetweenUsers(String sender, String receiver) {
        if(sender == null || receiver == null){
            log.warn("No se eliminaron los mensajes entre el remitente: {} y el receptor: {}", sender, receiver);
            return;
        }else{
            log.debug("Eliminando mensajes entre el usuario {} y {}", sender, receiver);
            try{
                messageRepository.deleteAllByFromUserIdAndToUserId(sender, receiver);
                log.info("Mensajes entre el usuario {} y {} eliminados", sender, receiver);
            }catch(Exception ex){
                log.error("Ocurrio un error al intentar eliminar los mensajes entre: {} y {}: {}", sender, receiver, ex.getMessage());
                throw new RuntimeException("Error al eliminar los mensajes entre remitente y receptor", ex);
            }
            
        }
    }

    /* 
    @Transactional
    public void savePendingDeletion(String recipient, String messageId) {
        if(recipient == null || messageId == null){
            log.warn("No se pudo guardar la solicitud de eliminacion de mensaje pendinte: {} para el usuario: {}", messageId, recipient);
            return;
        }else{
            try{
                log.debug("Intentando guardar solicitud de eliminacion");
                PendingDeletion pd = new PendingDeletion(null, recipient, messageId);
                pendingDeletionRepository.save(pd);
                log.info("Solicitud de eliminacion guardada");
            }catch(Exception ex){
                log.error("No se pudo guardar la solicitud de eliminacion: {}", ex.getMessage());
                throw new RuntimeException("Error al guardar la solicitud de eliminacion pendiente", ex);
            }
        }
        
    }*/

    @Transactional
    public List<String> getAndClearPendingDeletions(String recipient) {
        if(recipient == null){
            log.warn("No se pudieron borrar las eliminaciones pendientes para el usuario: {}", recipient);
            return new ArrayList<>();
        }

        try{
            log.debug("Buscando eliminaciones pendientes para usuario: {}", recipient);

            List<PendingDeletion> pending = pendingDeletionRepository.findByRecipient(recipient);

            if(!pending.isEmpty()){
                log.info("Se hallaron {} eliminaciones pendientes para usuario: {}", pending.size(), recipient);

                List<String> ids = pending.stream().map(PendingDeletion::getMessageId).toList();
                pendingDeletionRepository.deleteAll(pending);

                log.info("Se eliminaron {} registros pendientes para usuario: {}", pending.size(), recipient);
                return ids;

            }else{
                log.debug("No hay eliminaciones pendientes para usuario: {}", recipient);
                return new ArrayList<>();
            }
        }catch(Exception ex){
            log.error("Error al obtener/limpiar eliminaciones pendientes para usuario {}: {}", recipient, ex.getMessage(), ex);
            throw new RuntimeException("Error al procesar eliminaciones pendientes", ex);
        }
    }

    @Transactional
    public void savePendingReadReceipts(String receiptRecipient, List<String> messageIds, String reader) {
        if(receiptRecipient == null || messageIds.isEmpty() || reader == null || messageIds == null){
            log.warn("No se pudo guardar las confirmaciones de lectura del usuario: {}, lista de IDs: {}", receiptRecipient, messageIds);
            return;
        }
        
        try{
            log.debug("Mapeando y guardando {} confirmaciones de lectura", messageIds.size());
            List<PendingReadReceipt> receipts = messageIds.stream()
                .map(msgId -> new PendingReadReceipt(null, msgId, receiptRecipient, reader))
                .toList();
            
            log.info("Guardando confirmaciones de lectura");
            pendingReadReceiptRepository.saveAll(receipts);
        }catch (Exception ex){
            log.error("Error al guardar confirmaciones de lectura {}", ex.getMessage());
            throw new RuntimeException("Error procesando solicitudes de lectura", ex);
        }
        
    }

    @Transactional
    public List<PendingReadReceipt> getAndClearPendingReadReceipts(String receiptRecipient){

        if(receiptRecipient == null){
            log.warn("Parametro invalido: {}", receiptRecipient);
            return new ArrayList<>();
        }

        try{
            log.debug("Oteniendo lista de confirmaciones de lectura para: {}", receiptRecipient);

            List<PendingReadReceipt> pending = pendingReadReceiptRepository.findByReceiptRecipient(receiptRecipient);

            if (!pending.isEmpty()) {
            log.info("Eliminando {} confirmaciones de lectura para: {}", pending.size(), receiptRecipient);
            pendingReadReceiptRepository.deleteAll(pending);
            }else{
                log.debug("No hay confirmaciones pendientes para: {}", receiptRecipient);
            }
            return pending;
        }catch(Exception ex){
            log.error("Error critico eliminando las confirmaciones de lectura para: {}: {}", receiptRecipient, ex.getMessage());
            throw new RuntimeException("Error al intentar eliminar las confirmaciones de lectura", ex);
        }
        
        
        
    }

    /**
     * Obtiene y elimina las limpiezas de historial pendientes para un usuario.
     *
     * @param recipientUsername Nombre del usuario que recibe las limpiezas
     * @return Lista de entidades PendingClearHistory
     */
    @Transactional
    public List<PendingClearHistory> getAndClearPendingClearHistories(String recipientUsername) {
        if (recipientUsername == null) {
            log.warn("Parámetro inválido: recipientUsername es nulo");
            return new ArrayList<>();
        }

        try {
            log.debug("Obteniendo limpiezas de historial pendientes para usuario: {}", recipientUsername);
            List<PendingClearHistory> pending = pendingClearHistoryRepository.findByRecipient(recipientUsername);

            if (!pending.isEmpty()) {
                log.info("Encontradas {} limpiezas de historial pendientes para usuario: {}", 
                        pending.size(), recipientUsername);
                pendingClearHistoryRepository.deleteAll(pending);
                log.info("Limpiezas de historial eliminadas después de entrega para usuario: {}", recipientUsername);
            } else {
                log.debug("No hay limpiezas de historial pendientes para usuario: {}", recipientUsername);
            }

            return pending;
        } catch (Exception ex) {
            log.error("Error al obtener/limpiar limpiezas de historial pendientes para usuario {}: {}", 
                    recipientUsername, ex.getMessage(), ex);
            throw new RuntimeException("Error al procesar limpiezas de historial pendientes", ex);
        }
    }

    /**
     * Obtiene y elimina los bloqueos pendientes para un usuario.
     *
     * @param recipientUsername Nombre del usuario que recibe los bloqueos
     * @return Lista de entidades PendingBlock
     */
    @Transactional
    public List<PendingBlock> getAndClearPendingBlocks(String recipientUsername) {
        if (recipientUsername == null) {
            log.warn("Parámetro inválido: recipientUsername es nulo");
            return new ArrayList<>();
        }

        try {
            log.debug("Obteniendo bloqueos pendientes para usuario: {}", recipientUsername);
            List<PendingBlock> pending = pendingBlockRepository.findByBlockedUser(recipientUsername);

            if (!pending.isEmpty()) {
                log.info("Encontrados {} bloqueos pendientes para usuario: {}", 
                        pending.size(), recipientUsername);
                pendingBlockRepository.deleteAll(pending);
                log.info("Bloqueos eliminados después de entrega para usuario: {}", recipientUsername);
            } else {
                log.debug("No hay bloqueos pendientes para usuario: {}", recipientUsername);
            }

            return pending;
        } catch (Exception ex) {
            log.error("Error al obtener/limpiar bloqueos pendientes para usuario {}: {}", 
                    recipientUsername, ex.getMessage(), ex);
            throw new RuntimeException("Error al procesar bloqueos pendientes", ex);
        }
    }

    /**
     * Obtiene y elimina los desbloqueos pendientes para un usuario.
     *
     * @param recipientUsername Nombre del usuario que recibe los desbloqueos
     * @return Lista de entidades PendingUnblock
     */
    @Transactional
    public List<PendingUnblock> getAndClearPendingUnblocks(String recipientUsername) {
        if (recipientUsername == null) {
            log.warn("Parámetro inválido: recipientUsername es nulo");
            return new ArrayList<>();
        }

        try {
            log.debug("Obteniendo desbloqueos pendientes para usuario: {}", recipientUsername);
            List<PendingUnblock> pending = pendingUnblockRepository.findByUnblockedUser(recipientUsername);

            if (!pending.isEmpty()) {
                log.info("Encontrados {} desbloqueos pendientes para usuario: {}", 
                        pending.size(), recipientUsername);
                pendingUnblockRepository.deleteAll(pending);
                log.info("Desbloqueos eliminados después de entrega para usuario: {}", recipientUsername);
            } else {
                log.debug("No hay desbloqueos pendientes para usuario: {}", recipientUsername);
            }

            return pending;
        } catch (Exception ex) {
            log.error("Error al obtener/limpiar desbloqueos pendientes para usuario {}: {}", 
                    recipientUsername, ex.getMessage(), ex);
            throw new RuntimeException("Error al procesar desbloqueos pendientes", ex);
        }
    }

    /**
     * Obtiene y elimina las identidades de contacto pendientes para un usuario.
     *
     * @param recipientUsername Nombre del usuario que recibe las actualizaciones de identidad
     * @return Lista de entidades PendingContactIdentity
     */
    @Transactional
    public List<PendingContactIdentity> getAndClearPendingContactIdentities(String recipientUsername) {
        if (recipientUsername == null) {
            log.warn("Parámetro inválido: recipientUsername es nulo");
            return new ArrayList<>();
        }

        try {
            log.debug("Obteniendo identidades de contacto pendientes para usuario: {}", recipientUsername);
            List<PendingContactIdentity> pending = pendingContactIdentityRepository.findByRecipient(recipientUsername);

            if (!pending.isEmpty()) {
                log.info("Encontradas {} identidades de contacto pendientes para usuario: {}", 
                        pending.size(), recipientUsername);
                pendingContactIdentityRepository.deleteAll(pending);
                log.info("Identidades de contacto eliminadas después de entrega para usuario: {}", recipientUsername);
            } else {
                log.debug("No hay identidades de contacto pendientes para usuario: {}", recipientUsername);
            }

            return pending;
        } catch (Exception ex) {
            log.error("Error al obtener/limpiar identidades de contacto pendientes para usuario {}: {}", 
                    recipientUsername, ex.getMessage(), ex);
            throw new RuntimeException("Error al procesar identidades de contacto pendientes", ex);
        }
    }

    /**
     * Obtiene TODOS los mensajes pendientes para un usuario.
     * Este método es llamado por MessageController cuando un usuario se conecta.
     * Recopila todos los tipos de pendientes (mensajes no leídos, eliminaciones, bloqueos,
     * desbloqueos, limpiezas de historial, confirmaciones de lectura, identidades) en un solo WsMessage.
     *
     * @param username Nombre del usuario que solicita sus pendientes
     * @return WsMessage conteniendo todos los pendientes, o null si no hay ninguno
     */
    public MessagesProto.WsMessage getAllPendingMessages(String username) {
        log.info("Obteniendo todos los mensajes pendientes para usuario: {}", username);

        try {
            MessagesProto.WsMessage.Builder wsBuilder = MessagesProto.WsMessage.newBuilder();
            boolean hasAnyPending = false;

            // 1. Obtener mensajes de chat no leídos
            List<ChatMessage> unreadMessages = getUnreadMessages(username);
            if (!unreadMessages.isEmpty()) {
                MessagesProto.UnreadMessagesList unreadList = MessagesProto.UnreadMessagesList.newBuilder()
                        .addAllMessages(unreadMessages)
                        .build();
                wsBuilder.setUnreadMessagesList(unreadList);
                log.info("Agregados {} mensajes no leídos para usuario: {}", unreadMessages.size(), username);
                hasAnyPending = true;
            }

            // 2. Obtener bloqueos pendientes
            List<PendingBlock> pendingBlocks = getAndClearPendingBlocks(username);
            if (!pendingBlocks.isEmpty()) {
                MessagesProto.BlockedUsersList.Builder blockedListBuilder = MessagesProto.BlockedUsersList.newBuilder();
                for (PendingBlock pb : pendingBlocks) {
                    // getBlocker() es el usuario que realizó el bloqueo
                    blockedListBuilder.addUsers(pb.getBlocker());
                }
                wsBuilder.setBlockedUsersList(blockedListBuilder.build());
                log.info("Agregados {} bloqueos pendientes para usuario: {}", pendingBlocks.size(), username);
                hasAnyPending = true;
            }

            // 3. Obtener desbloqueos pendientes
            List<PendingUnblock> pendingUnblocks = getAndClearPendingUnblocks(username);
            if (!pendingUnblocks.isEmpty()) {
                MessagesProto.UnblockedUsersList.Builder unblockedListBuilder = MessagesProto.UnblockedUsersList.newBuilder();
                for (PendingUnblock pu : pendingUnblocks) {
                    // getBlocker() es el usuario que realizó el desbloqueo
                    unblockedListBuilder.addUsers(pu.getBlocker());
                }
                wsBuilder.setUnblockedUsersList(unblockedListBuilder.build());
                log.info("Agregados {} desbloqueos pendientes para usuario: {}", pendingUnblocks.size(), username);
                hasAnyPending = true;
            }

            // 4. Obtener limpiezas de historial pendientes
            List<PendingClearHistory> pendingClearHistories = getAndClearPendingClearHistories(username);
            if (!pendingClearHistories.isEmpty()) {
                MessagesProto.PendingClearHistoryList.Builder clearHistoryListBuilder = 
                    MessagesProto.PendingClearHistoryList.newBuilder();
                for (PendingClearHistory pch : pendingClearHistories) {
                    log.debug("Agregando solicitud de limpieza de: {} para: {}", 
                              pch.getSender(), pch.getRecipient());
                    MessagesProto.PendingClearHistory clearHistoryProto = 
                        MessagesProto.PendingClearHistory.newBuilder()
                            .setSender(pch.getSender())
                            .setRecipient(pch.getRecipient())
                            .build();
                    clearHistoryListBuilder.addPendingClearHistory(clearHistoryProto);
                }
                wsBuilder.setPendingClearHistoryList(clearHistoryListBuilder.build());
                log.info("Agregadas {} limpiezas de historial pendientes para usuario: {}", 
                         pendingClearHistories.size(), username);
                hasAnyPending = true;
            }

            // 5. Obtener confirmaciones de lectura pendientes
            List<PendingReadReceipt> pendingReadReceipts = getAndClearPendingReadReceipts(username);
            if (!pendingReadReceipts.isEmpty()) {
                MessagesProto.MessagesReadUpdate.Builder readUpdateBuilder = MessagesProto.MessagesReadUpdate.newBuilder();
                for (PendingReadReceipt prr : pendingReadReceipts) {
                    readUpdateBuilder.addMessageIds(prr.getMessageId())
                            .setReaderUsername(prr.getReader());
                }
                wsBuilder.setMessagesReadUpdate(readUpdateBuilder.build());
                log.info("Agregadas {} confirmaciones de lectura pendientes para usuario: {}", 
                         pendingReadReceipts.size(), username);
                hasAnyPending = true;
            }

            // 6. Obtener identidades de contacto pendientes
            List<PendingContactIdentity> pendingIdentities = getAndClearPendingContactIdentities(username);
            if (!pendingIdentities.isEmpty()) {
                for (PendingContactIdentity pci : pendingIdentities) {
                    MessagesProto.ContactIdentity identity = MessagesProto.ContactIdentity.newBuilder()
                            .setSenderId(pci.getSenderId())
                            .setSenderUsername(pci.getSenderUsername())
                            .build();
                    wsBuilder.setContactIdentity(identity);
                }
                log.info("Agregadas {} identidades de contacto pendientes para usuario: {}", 
                         pendingIdentities.size(), username);
                hasAnyPending = true;
            }

            if (hasAnyPending) {
                log.info("Se encontraron mensajes pendientes para usuario: {}", username);
                return wsBuilder.build();
            } else {
                log.info("No se encontraron mensajes pendientes para usuario: {}", username);
                return null;
            }

        } catch (Exception e) {
            log.error("Error al obtener todos los mensajes pendientes para usuario {}: {}", 
                    username, e.getMessage(), e);
            throw new RuntimeException("Error al obtener mensajes pendientes", e);
        }
    }
}
