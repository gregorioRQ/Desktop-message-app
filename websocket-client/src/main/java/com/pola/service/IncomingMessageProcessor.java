package com.pola.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pola.model.ChatMessage;
import com.pola.model.Contact;
import com.pola.model.ImageChatMessage;
import com.pola.model.Notification;
import com.pola.proto.ImageMessage;
import com.pola.proto.MessagesProto;
import com.pola.proto.MessagesProto.WsMessage;
import com.pola.proto.MessagesProto.PendingClearHistoryList;
import com.pola.util.MessageProcessingContext;
import javafx.application.Platform;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.pola.config.HttpConfig;

/**
 * Procesa los mensajes entrantes del WebSocket.
 * Principio SOLID: Open/Closed - Fácil de extender con nuevos handlers en el mapa.
 */
public class IncomingMessageProcessor {
    private static final Logger log = LoggerFactory.getLogger(IncomingMessageProcessor.class);
    
    private final MessageProcessingContext context;
    private final Map<WsMessage.PayloadCase, Consumer<WsMessage>> handlers = new HashMap<>();
    private Consumer<String> errorListener;
    
    // Debounce timers para confirmación de lectura
    // Evita efecto rebote cuando llegan múltiples mensajes mientras el chat está abierto
    // Cada contacto tiene su propio timer que se resetea con cada nuevo mensaje
    private final Map<String, ScheduledFuture<?>> readReceiptTimers = new HashMap<>();
    private final ScheduledExecutorService scheduler;

    public IncomingMessageProcessor(MessageProcessingContext context) {
        this(context, Executors.newSingleThreadScheduledExecutor());
    }

    // Constructor para testing - permite inyectar scheduler mockeado
    IncomingMessageProcessor(MessageProcessingContext context, ScheduledExecutorService scheduler) {
        this.context = context;
        this.scheduler = scheduler;
        initializeHandlers();
    }

    // Getter para testing - permite verificar timers
    Map<String, ScheduledFuture<?>> getReadReceiptTimers() {
        return readReceiptTimers;
    }

    public MessageProcessingContext getContext() {
        return context;
    }

    public void setErrorListener(Consumer<String> listener) {
        this.errorListener = listener;
    }

    /**
     * Inicializa el mapa de handlers para cada tipo de mensaje entrante.
     * Cada handler procesa un tipo específico de mensaje Protobuf.
     * 
     * NOTA: DELETE_MESSAGE_REQUEST no se maneja aquí porque ese tipo de mensaje
     * es enviado por el cliente al servidor, no del servidor al cliente.
     * El servidor envía MessageDeletedNotification cuando otro usuario elimina un mensaje.
     */
      private void initializeHandlers() {
          handlers.put(WsMessage.PayloadCase.CHAT_MESSAGE_RESPONSE, this::handleChatMessageResponse);
          handlers.put(WsMessage.PayloadCase.UNREAD_MESSAGES_LIST, msg -> processUnreadMessages(msg.getUnreadMessagesList()));
          // MessageDeletedNotification: recibida cuando otro usuario elimina un mensaje "para todos"
          handlers.put(WsMessage.PayloadCase.MESSAGE_DELETE_NOTIFICATION, msg -> processMessageDeletedNotification(msg.getMessageDeleteNotification()));
          handlers.put(WsMessage.PayloadCase.CLEAR_HISTORY_REQUEST, msg -> processClearHistoryRequest(msg.getClearHistoryRequest()));
          
          // PendingClearHistoryList: lista de solicitudes pendientes de limpieza de historial
          handlers.put(WsMessage.PayloadCase.PENDING_CLEAR_HISTORY_LIST, msg -> processPendingClearHistoryList(msg.getPendingClearHistoryList()));
          
          handlers.put(WsMessage.PayloadCase.CHAT_MESSAGE, this::handleChatMessage);
          handlers.put(WsMessage.PayloadCase.UNBLOCKED_USERS_LIST, msg -> processUnblockedUsersList(msg.getUnblockedUsersList()));
          handlers.put(WsMessage.PayloadCase.BLOCKED_USERS_LIST, msg -> processBlockedUsersList(msg.getBlockedUsersList()));
          handlers.put(WsMessage.PayloadCase.MESSAGES_READ_UPDATE, msg -> processMessagesReadUpdate(msg.getMessagesReadUpdate()));
          // Handler para solicitud de marcar mensajes como leídos (recibido cuando otro usuario lee nuestros mensajes)
          handlers.put(WsMessage.PayloadCase.MARK_MESSAGES_AS_READ_REQUEST, msg -> processMarkMessagesAsReadRequest(msg.getMarkMessagesAsReadRequest()));
          // Handlers para solicitudes de bloqueo y desbloqueo de contactos
          handlers.put(WsMessage.PayloadCase.BLOCK_CONTACT_REQUEST, this::processBlockContactRequest);
          handlers.put(WsMessage.PayloadCase.UNBLOCK_CONTACT_REQUEST, this::processUnblockContactRequest);
          handlers.put(WsMessage.PayloadCase.IMAGE_MESSAGE, this::handleImageMessage);
          // Handler para lista de mensajes de imagen pendientes (recibidos al conectarse)
          handlers.put(WsMessage.PayloadCase.UNREAD_IMAGE_MESSAGES_LIST, msg -> processUnreadImageMessages(msg.getUnreadImageMessagesList()));
          // Handler para respuesta de presencia de contactos
handlers.put(WsMessage.PayloadCase.CONTACT_PRESENCE_MESSAGE, msg -> processContactPresenceMessage(msg.getContactPresenceMessage()));
        // Handler para notificación de mensajes entregados al destinatario
        handlers.put(WsMessage.PayloadCase.MESSAGE_DELIVERED_UPDATE, msg -> processMessageDeliveredUpdate(msg.getMessageDeliveredUpdate()));
        // Handler para lista de actualizaciones de lectura (múltiples readers)
        handlers.put(WsMessage.PayloadCase.MESSAGES_READ_UPDATE_LIST, msg -> processMessagesReadUpdateList(msg.getMessagesReadUpdateList()));
    }

    public void process(WsMessage message) {
        Consumer<WsMessage> handler = handlers.get(message.getPayloadCase());
        if (handler != null) {
            handler.accept(message);
        } else {
            System.out.println("Tipo de mensaje no manejado: " + message.getPayloadCase());
        }
    }

    private void handleChatMessageResponse(WsMessage wsMessage) {
        MessagesProto.ChatMessageResponse response = wsMessage.getChatMessageResponse();
        String messageIdStr = response.getMessageId();
        
        if (messageIdStr == null || messageIdStr.isEmpty()) {
            return;
        }
        
        long messageId;
        try {
            messageId = Long.parseLong(messageIdStr);
        } catch (NumberFormatException e) {
            return;
        }
        
        if (response.getCause() == MessagesProto.FailureCause.BLOCKED) {
            String recipient = response.getRecipient();
            context.getContactService().markUserAsBlockingMe(recipient);

            try {
                context.getMessageRepository().delete(messageId);
                Platform.runLater(() -> context.getCurrentChatMessages().removeIf(m -> m.getId() == messageId));
            } catch (Exception e) {
                e.printStackTrace();
            }

            Contact current = context.getCurrentContactSupplier().get();
            if (current != null && current.getContactUsername().equals(recipient)) {
                Platform.runLater(() -> {
                    ChatMessage systemMessage = new ChatMessage(recipient, "Sistema", response.getErrorMessage(), "Sistema");
                    systemMessage.setId(System.currentTimeMillis());
                    context.getCurrentChatMessages().add(systemMessage);
                });
            }
            return;
        }
        
        final long msgId = messageId;
        ChatMessage.MessageStatus newStatus = response.getSuccess() ? ChatMessage.MessageStatus.SENT : ChatMessage.MessageStatus.FAILED;

        try {
            context.getMessageRepository().updateStatus(msgId, newStatus);
        } catch (SQLException e) {
            log.error("Error al actualizar estado del mensaje {}: {}", msgId, e.getMessage());
        }

        Platform.runLater(() -> {
            for (int i = 0; i < context.getCurrentChatMessages().size(); i++) {
                ChatMessage msg = context.getCurrentChatMessages().get(i);
                if (msg.getId() == msgId) {
                    msg.setStatus(newStatus);
                    context.getCurrentChatMessages().set(i, msg);
                    break;
                }
            }
        });
    }
    
    @Deprecated
    private void handleMessageError(WsMessage wsMessage) {
        handleChatMessageResponse(wsMessage);
    }

    private void handleChatMessage(WsMessage wsMessage) {
        MessagesProto.ChatMessage protobufMessage = wsMessage.getChatMessage();
        String senderId = protobufMessage.getSender();
        String content = protobufMessage.getContent();
        long messageId = Long.parseLong(protobufMessage.getId());
        
        // LOG TEMPORAL: Ver si llegan mensajes de usuarios no registrados con su ID
        System.out.println("[DEBUG] Mensaje recibido de senderId: " + senderId);
        
        String currentUserId = context.getCurrentUserIdSupplier().get();
        boolean isOwnMessage = senderId.equals(currentUserId);
        
        try {
            if (context.getMessageRepository().existsById(messageId)) return;

            Contact contact = context.getContactService().findContactByUsername(context.getCurrentUserIdSupplier().get(), senderId)
            //añade un contacto "improvisado" con un id no oficial
                .orElseGet(() -> context.getContactService().addContact(context.getCurrentUserIdSupplier().get(), senderId));

            if(contact == null) return;

            ChatMessage localMessage = new ChatMessage(contact.getContactUsername(), senderId, content, senderId);
            localMessage.setId(messageId);
            
            if (isOwnMessage) {
                localMessage.setStatus(ChatMessage.MessageStatus.DELIVERED);
            }
            
            ChatMessage saved = context.getMessageRepository().create(localMessage);

            Contact current = context.getCurrentContactSupplier().get();
            if(current != null && current.getId() == contact.getId()){
                Platform.runLater(() -> context.getCurrentChatMessages().add(saved));
                scheduleReadReceipt(senderId);
            } else {
                updateNotification(senderId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void handleImageMessage(WsMessage wsMessage) {
        ImageMessage protoImage = wsMessage.getImageMessage();
        
        String mediaId = protoImage.getMediaId();
        String senderId = protoImage.getSenderId();
        String receiverId = protoImage.getReceiverId();
        String fullImageUrl = protoImage.getFullImageUrl();
        int width = protoImage.getOriginalWidth();
        int height = protoImage.getOriginalHeight();
        long timestamp = protoImage.getTimestamp();
        
        System.out.println("Recibido ImageMessage - mediaId: " + mediaId + ", sender: " + senderId + ", url: " + fullImageUrl);
        
        try {
            Contact contact = context.getContactService().findContactByUsername(context.getCurrentUserIdSupplier().get(), senderId)
                .orElseGet(() -> context.getContactService().addContact(context.getCurrentUserIdSupplier().get(), senderId));
            
            if (contact == null) return;
            
            ImageChatMessage imageMessage = new ImageChatMessage(
                contact.getContactUsername(),
                senderId,
                fullImageUrl,
                mediaId,
                width,
                height
            );
            imageMessage.setId(timestamp);
            imageMessage.setDownloaded(false);
            
            ObjectMapper mapper = new ObjectMapper();
            String contentJson = mapper.writeValueAsString(new ImageContent(fullImageUrl, mediaId, width, height));
            
            context.getMessageRepository().create(imageMessage, "image", false, contentJson);
            
            Contact current = context.getCurrentContactSupplier().get();
            if (current != null && current.getId() == contact.getId()) {
                Platform.runLater(() -> context.getCurrentChatMessages().add(imageMessage));
            } else {
                updateNotification(senderId);
            }
        } catch (Exception e) {
            System.err.println("Error al procesar ImageMessage: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static class ImageContent {
        public String imageUrl;
        public String mediaId;
        public int width;
        public int height;
        
        public ImageContent() {}
        
        public ImageContent(String imageUrl, String mediaId, int width, int height) {
            this.imageUrl = imageUrl;
            this.mediaId = mediaId;
            this.width = width;
            this.height = height;
        }
    }

    /**
     * Procesa la notificación de eliminación de mensaje recibida del servidor.
     * 
     * Esta notificación es enviada cuando OTRO usuario elimina un mensaje "para todos".
     * El cliente debe eliminar el mensaje de su base de datos local y de la UI.
     * 
     * NOTA: Esta NO es una DeleteMessageRequest (que es enviada por el propio cliente al servidor).
     * El servidor envía MessageDeletedNotification cuando un mensaje es eliminado por otro usuario.
     * 
     * @param notification la notificación de eliminación con el ID del mensaje y quién lo eliminó
     */
    private void processMessageDeletedNotification(MessagesProto.MessageDeletedNotification notification) {
        try {
            long messageId = Long.parseLong(notification.getMessageId());
            String deletedBy = notification.getDeletedBy();
            
            // Eliminar de la base de datos local
            context.getMessageRepository().delete(messageId);
            
            // Eliminar de la UI si el mensaje está visible
            Platform.runLater(() -> {
                boolean removed = context.getCurrentChatMessages().removeIf(m -> m.getId() == messageId);
                if (removed) {
                    System.out.println("Mensaje " + messageId + " eliminado de la vista por eliminación de: " + deletedBy);
                } else {
                    System.out.println("Mensaje " + messageId + " eliminado de BD (no estaba en vista)");
                }
            });
            
            System.out.println("MessageDeletedNotification procesada - mensajeID: " + messageId + ", eliminadoPor: " + deletedBy);
            
        } catch (Exception e) {
            System.err.println("Error al procesar MessageDeletedNotification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Procesa una solicitud directa de limpieza de historial recibida en tiempo real.
     * Este método se invoca cuando el usuario RECIBE una solicitud de ClearHistoryRequest
     * del otro usuario mientras está conectado (en tiempo real).
     * 
     * Diferencia con processPendingClearHistoryList:
     * - processClearHistoryRequest: El otro usuario eliminó historial contigo MIENTRAS estabas online
     * - processPendingClearHistoryList: El otro usuario eliminó historial contigo mientras estabas offline
     * 
     * En ambos casos, se elimina el historial local con el usuario que solicitó la limpieza.
     * 
     * @param request Solicitud de limpieza de historial recibida
     */
    private void processClearHistoryRequest(MessagesProto.ClearHistoryRequest request) {
        String senderUsername = request.getSender();
        log.info("Recibida solicitud de limpieza de historial en tiempo real - Solicitante: {}", senderUsername);
        System.out.println("Recibida solicitud de limpieza de historial en tiempo real - Solicitante: " + senderUsername);
        
        try {
            // Eliminar todos los mensajes con el usuario que solicitó la limpieza
            context.getMessageRepository().deleteByContactUsername(senderUsername);
            log.info("Historial eliminado para contacto: {}", senderUsername);
            
            // Si estamos viendo el chat de este contacto, limpiar la UI
            Contact current = context.getCurrentContactSupplier().get();
            if (current != null && current.getContactUsername().equals(senderUsername)) {
                // Limpia la ui de los mensajes.
                Platform.runLater(() -> context.getCurrentChatMessages().clear());
                log.debug("Vista de chat limpiada para contacto: {}", senderUsername);
            }
        } catch (SQLException e) {
            log.error("Error al procesar solicitud de limpieza de historial para {}: {}", senderUsername, e.getMessage());
            e.printStackTrace();
        }
    }

    private void processUnreadMessages(MessagesProto.UnreadMessagesList unreadMessagesList) {
        Set<String> sendersToNotify = new HashSet<>();

        for (MessagesProto.ChatMessage protoMessage : unreadMessagesList.getMessagesList()) {
            String senderUsername = protoMessage.getSender();
            long messageId = Long.parseLong(protoMessage.getId());

            try {
                Contact contact = context.getContactService().findContactByUsername(context.getCurrentUserIdSupplier().get(), senderUsername)
                    .orElseGet(() -> context.getContactService().addContact(context.getCurrentUserIdSupplier().get(), senderUsername));

                if (contact == null || context.getMessageRepository().existsById(messageId)) continue;

                ChatMessage localMessage = new ChatMessage(contact.getContactUsername(), senderUsername, protoMessage.getContent(), senderUsername);
                localMessage.setId(messageId);
                ChatMessage saved = context.getMessageRepository().create(localMessage);

                Contact current = context.getCurrentContactSupplier().get();
                if (current != null && current.getId() == contact.getId()) {
                    Platform.runLater(() -> context.getCurrentChatMessages().add(saved));
                } else {
                    updateNotification(senderUsername);
                }
                sendersToNotify.add(senderUsername);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        for (String senderUsername : sendersToNotify) {
            scheduleReadReceipt(senderUsername);
        }
    }

private void processMessagesReadUpdate(MessagesProto.MessagesReadUpdate update) {
        List<String> idsStr = update.getMessageIdsList();
        String readerUsername = update.getReaderUsername();

        log.info("=== RECIBIDO MessagesReadUpdate === Reader: {}, IDs: {}", readerUsername, idsStr);

        if (idsStr.isEmpty()) return;

        List<Long> ids = new java.util.ArrayList<>();
        for(String s : idsStr) {
            try { ids.add(Long.parseLong(s)); } catch (NumberFormatException e) {}
        }

        try {
            context.getMessageRepository().updateMultipleStatus(ids, ChatMessage.MessageStatus.READ);
            Platform.runLater(() -> {
                for (int i = 0; i < context.getCurrentChatMessages().size(); i++) {
                    ChatMessage msg = context.getCurrentChatMessages().get(i);
                    if (ids.contains(msg.getId())) {
                        msg.setRead(true);
                        msg.setStatus(ChatMessage.MessageStatus.READ);
                        context.getCurrentChatMessages().set(i, msg);
                        log.info("Mensaje {} marcado como leido en UI", msg.getId());
                    }
                }
                if (context.getOnMessagesUpdated() != null) {
                    context.getOnMessagesUpdated().run();
                }
            });
log.info("Procesado MessagesReadUpdate para {} mensajes", ids.size());
    } catch (SQLException e) {
        log.error("Error al procesar MessagesReadUpdate: {}", e.getMessage());
        e.printStackTrace();
    }
    }

    private void processMessagesReadUpdateList(MessagesProto.MessagesReadUpdateList updateList) {
        log.info("=== RECIBIDO MessagesReadUpdateList === {} actualizaciones", updateList.getUpdatesCount());

        for (MessagesProto.MessagesReadUpdate update : updateList.getUpdatesList()) {
            processMessagesReadUpdate(update);
        }
    }

    private void processMessageDeliveredUpdate(MessagesProto.MessageDeliveredUpdate update) {
        List<String> idsStr = update.getMessageIdsList();
        String deliveredTo = update.getDeliveredToUsername();

        log.info("=== RECIBIDO MessageDeliveredUpdate === Entregado a: {}, IDs: {}", deliveredTo, idsStr);

        if (idsStr.isEmpty()) return;

        List<Long> ids = new java.util.ArrayList<>();
        for (String s : idsStr) {
            try { ids.add(Long.parseLong(s)); } catch (NumberFormatException e) {}
        }

        try {
            context.getMessageRepository().updateMultipleStatus(ids, ChatMessage.MessageStatus.DELIVERED);
            Platform.runLater(() -> {
                for (int i = 0; i < context.getCurrentChatMessages().size(); i++) {
                    ChatMessage msg = context.getCurrentChatMessages().get(i);
                    if (ids.contains(msg.getId())) {
                        msg.setStatus(ChatMessage.MessageStatus.DELIVERED);
                        context.getCurrentChatMessages().set(i, msg);
                        log.info("Mensaje {} actualizado a DELIVERED en UI", msg.getId());
                    }
                }
                if (context.getOnMessagesUpdated() != null) {
                    context.getOnMessagesUpdated().run();
                }
            });
            log.info("Procesado MessageDeliveredUpdate para {} mensajes", ids.size());
        } catch (SQLException e) {
            log.error("Error al procesar MessageDeliveredUpdate: {}", e.getMessage());
            e.printStackTrace();
        }
    }

    private void processMarkMessagesAsReadRequest(MessagesProto.MarkMessagesAsReadRequest request) {
        String sender = request.getSender();
        String recipient = request.getRecipient();
        List<String> idsStr = request.getMessageIdsList();
        
        if (idsStr.isEmpty()) return;
        
        List<Long> ids = new java.util.ArrayList<>();
        for (String s : idsStr) {
            try { ids.add(Long.parseLong(s)); } catch (NumberFormatException e) {}
        }
        
        try {
            context.getMessageRepository().updateMultipleStatus(ids, ChatMessage.MessageStatus.READ);
            Platform.runLater(() -> {
                for (int i = 0; i < context.getCurrentChatMessages().size(); i++) {
                    ChatMessage msg = context.getCurrentChatMessages().get(i);
                    if (ids.contains(msg.getId())) {
                        msg.setRead(true);
                        msg.setStatus(ChatMessage.MessageStatus.READ);
                        context.getCurrentChatMessages().set(i, msg);
                    }
                }
                if (context.getOnMessagesUpdated() != null) {
                    context.getOnMessagesUpdated().run();
                }
            });
            log.info("Marked {} messages as read from {} to {}", ids.size(), sender, recipient);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void processBlockedUsersList(MessagesProto.BlockedUsersList list) {
        processUserStatusChange(list.getUsersList(), "Este usuario te ha bloqueado.", context.getContactService()::markUserAsBlockingMe);
    }

    private void processUnblockedUsersList(MessagesProto.UnblockedUsersList list) {
        processUserStatusChange(list.getUsersList(), "Este usuario te ha desbloqueado.", context.getContactService()::markUserAsUnblockingMe);
    }

    private void processUserStatusChange(List<String> users, String systemMsg, Consumer<String> action) {
        for (String username : users) {
            action.accept(username);
            try {
                ChatMessage localMessage = new ChatMessage(username, "Sistema", systemMsg, "Sistema");
                localMessage.setId(System.currentTimeMillis());
                context.getMessageRepository().create(localMessage);
                
                Contact current = context.getCurrentContactSupplier().get();
                if (current != null && current.getContactUsername().equals(username)) {
                    Platform.runLater(() -> context.getCurrentChatMessages().add(localMessage));
                } else {
                    updateNotification(username);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Procesa una lista de solicitudes pendientes de limpieza de historial.
     * Cada entrada representa un contacto que ha solicitado borrar todo el historial
     * de conversación con este usuario. Se eliminan localmente todos los mensajes
     * intercambiados con cada contacto.
     * 
     * Esta lista se recibe cuando el cliente se conecta después de haber estado offline.
     * El servidor envía las solicitudes de limpieza de historial que otros usuarios
     * hicieron mientras este usuario estaba desconectado.
     * 
     * Estructura esperada en cada PendingClearHistory:
     * - sender: Username del usuario que solicitó la limpieza (el otro usuario)
     * - recipient: Username de este usuario (quien debe aplicar la limpieza local)
     * 
     * Ejemplo: Si "juan" eliminó historial con "pedro", cuando "pedro" se conecte
     * recibirá PendingClearHistory con sender="juan", recipient="pedro".
     * 
     * @param pendingClearHistoryList Lista de solicitudes pendientes de limpieza de historial
     */
    private void processPendingClearHistoryList(PendingClearHistoryList pendingClearHistoryList) {
        log.info("=== Procesando PendingClearHistoryList con {} entradas ===", pendingClearHistoryList.getClearHistoriesCount());
        
        List<MessagesProto.PendingClearHistory> clearHistories = pendingClearHistoryList.getClearHistoriesList();
        if (clearHistories == null || clearHistories.isEmpty()) {
            log.debug("Lista de limpiezas pendientes vacía");
            return;
        }
        
        // Usar currentUsername para comparar con el campo recipient de PendingClearHistory
        // IMPORTANTE: PendingClearHistory.recipient contiene el username, NO el userId
        String currentUsername = context.getCurrentUsernameSupplier().get();
        if (currentUsername == null) {
            log.warn("No se puede procesar PendingClearHistoryList: currentUsername es null");
            System.out.println("No se puede procesar PendingClearHistoryList: currentUsername es null");
            return;
        }
        
        log.debug("Usuario actual para comparación: {}", currentUsername);
        
        Platform.runLater(() -> {
            int historialesEliminados = 0;
            int contactosNoEncontrados = 0;
            
            for (MessagesProto.PendingClearHistory clearHistory : clearHistories) {
                String senderUsername = clearHistory.getSender();
                String recipientUsername = clearHistory.getRecipient();
                
                log.debug("Procesando limpieza pendiente: sender={}, recipient={}", senderUsername, recipientUsername);
                System.out.println("Procesando limpieza pendiente: de " + senderUsername + " para " + recipientUsername);
                
                // Verificar que esta solicitud es para este usuario
                // IMPORTANTE: Comparar recipientUsername (username) con currentUsername, NO con currentUserId
                if (!recipientUsername.equals(currentUsername)) {
                    log.debug("Solicitud de limpieza ignorada: recipient {} no coincide con currentUsername {}", 
                        recipientUsername, currentUsername);
                    System.out.println("Solicitud de limpieza ignorada: recipient " + recipientUsername + " no coincide con currentUsername " + currentUsername);
                    continue;
                }
                
                try {
                    // Buscar el contacto por username (el que solicitó la limpieza)
                    // IMPORTANTE: findContactByUsername requiere userId (no username) como primer parámetro
                    String currentUserId = context.getCurrentUserIdSupplier().get();
                    Optional<Contact> contactOpt = context.getContactService()
                        .findContactByUsername(currentUserId, senderUsername);
                    
                    if (contactOpt.isPresent()) {
                        Contact contact = contactOpt.get();
                        String contactUsername = contact.getContactUsername();
                        
                        log.info("Eliminando historial con contacto: {} (solicitado por: {})", contactUsername, senderUsername);
                        System.out.println("Eliminando historial con contacto: " + contactUsername + " (solicitado por: " + senderUsername + ")");
                        
                        // Eliminar mensajes de la BD local (todos los mensajes con este contacto)
                        try {
                            context.getMessageRepository().deleteByContactUsername(contactUsername);
                            
                            // Si estamos viendo el chat de este contacto, limpiar la UI
                            Contact currentContact = context.getCurrentContactSupplier().get();
                            if (currentContact != null && 
                                currentContact.getContactUsername().equals(contactUsername)) {
                                context.getCurrentChatMessages().clear();
                                log.debug("Historial del contacto {} eliminado de la vista actual", contactUsername);
                            }
                            
                            historialesEliminados++;
                            log.info("Historial con usuario {} eliminado completamente", contactUsername);
                            System.out.println("Historial con usuario " + contactUsername + " (ID: " + senderUsername + ") eliminado completamente");
                        } catch (SQLException e) {
                            log.error("Error eliminando mensajes del contacto {}: {}", contactUsername, e.getMessage());
                            System.err.println("Error eliminando mensajes del contacto " + contactUsername + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    } else {
                        contactosNoEncontrados++;
                        log.warn("No se encontró contacto local para usuario: {}. La solicitud de limpieza se ignora.", senderUsername);
                        System.out.println("No se encontró contacto local para usuario: " + senderUsername + ". La solicitud de limpieza se ignora.");
                    }
                    
                } catch (Exception e) {
                    log.error("Error procesando PendingClearHistory para sender {}: {}", senderUsername, e.getMessage());
                    System.err.println("Error procesando PendingClearHistory para sender " + senderUsername + ": " + e.getMessage());
                    e.printStackTrace();
                }
            }
            
            log.info("=== Finalizado procesamiento de PendingClearHistoryList ===");
            log.info("Total procesados: {} historiales eliminados, {} contactos no encontrados", historialesEliminados, contactosNoEncontrados);
            System.out.println("=== Finalizado procesamiento de PendingClearHistoryList ===");
            System.out.println("Total procesados: " + historialesEliminados + " historiales eliminados, " + contactosNoEncontrados + " contactos no encontrados");
        });
    }

    /**
     * Procesa una solicitud de bloqueo de contacto recibida del servidor.
     * Cuando un usuario (sender) envía una solicitud para bloquear a otro usuario (recipient),
     * y el recipient está online, el servidor reenvía la solicitud para que el recipient
     * actualice su estado local.
     * 
     * NOTA: Esta solicitud NO significa que el receptor esté bloqueando al remitente.
     * Significa que el remitente quiere bloquear al receptor.
     * El receptor debe marcar al remitente como alguien que lo está bloqueando.
     * 
     * @param message Mensaje WsMessage que contiene BlockContactRequest
     */
    private void processBlockContactRequest(WsMessage message) {
        MessagesProto.BlockContactRequest request = message.getBlockContactRequest();
        String blockerUsername = request.getBlocker(); // El usuario que envía el bloqueo
        
        // LOG para debugging
        System.out.println("[BLOCK_REQUEST_RECEIVED] Usuario " + blockerUsername + " te ha bloqueado");
        
        // Marcar al blocker como alguien que nos está bloqueando
        context.getContactService().markUserAsBlockingMe(blockerUsername);
        
        // Actualizar notificaciones si es necesario
        updateNotification(blockerUsername);
    }

    /**
     * Procesa una solicitud de desbloqueo de contacto recibida del servidor.
     * Cuando un usuario (sender) envía una solicitud para desbloquear a otro usuario (recipient),
     * y el recipient está online, el servidor reenvía la solicitud para que el recipient
     * actualice su estado local.
     * 
     * NOTA: Esta solicitud significa que el remitente ya NO quiere bloquear al receptor.
     * El receptor debe marcar al remitente como alguien que ya no lo está bloqueando.
     * 
     * @param message Mensaje WsMessage que contiene UnblockContactRequest
     */
    private void processUnblockContactRequest(WsMessage message) {
        MessagesProto.UnblockContactRequest request = message.getUnblockContactRequest();
        String unblockerUsername = request.getBlocker(); // El usuario que envía el desbloqueo
        
        // LOG para debugging
        System.out.println("[UNBLOCK_REQUEST_RECEIVED] Usuario " + unblockerUsername + " te ha desbloqueado");
        
        // Marcar al unblocker como alguien que ya NO nos está bloqueando
        context.getContactService().markUserAsUnblockingMe(unblockerUsername);
        
        // Actualizar notificaciones si es necesario
        updateNotification(unblockerUsername);
    }

    /**
     * Processes the list of pending image messages received upon connection.
     * Each image is processed similar to a regular ImageMessage, creating the contact
     * if it doesn't exist and showing the corresponding notification.
     * Additionally, each image message is persisted to the local database with
     * type='image' and the content stored as JSON containing the URL and media ID.
     *
     * @param unreadImageMessagesList List of pending image messages
     */
    private void processUnreadImageMessages(MessagesProto.UnreadImageMessagesList unreadImageMessagesList) {
        System.out.println("Procesando lista de mensajes de imagen pendientes: " + unreadImageMessagesList.getMessagesCount() + " imágenes");
        
        ObjectMapper mapper = new ObjectMapper();
        
        for (ImageMessage protoImage : unreadImageMessagesList.getMessagesList()) {
            String senderId = protoImage.getSenderId();
            String mediaId = protoImage.getMediaId();
            String fullImageUrl = protoImage.getFullImageUrl();
            int width = protoImage.getOriginalWidth();
            int height = protoImage.getOriginalHeight();
            long timestamp = protoImage.getTimestamp();
            
            System.out.println("Procesando ImageMessage pendiente - mediaId: " + mediaId + ", sender: " + senderId);
            
            try {
                Contact contact = context.getContactService().findContactByUsername(context.getCurrentUserIdSupplier().get(), senderId)
                    .orElseGet(() -> context.getContactService().addContact(context.getCurrentUserIdSupplier().get(), senderId));
                
                if (contact == null) {
                    System.out.println("No se pudo crear contacto para sender: " + senderId);
                    continue;
                }
                
                ImageChatMessage imageMessage = new ImageChatMessage(
                    contact.getContactUsername(),
                    senderId,
                    fullImageUrl,
                    mediaId,
                    width,
                    height
                );
                imageMessage.setId(timestamp);
                imageMessage.setDownloaded(false);
                
                // Store image metadata as JSON in content column with type='image'
                String contentJson = mapper.writeValueAsString(new ImageContent(fullImageUrl, mediaId, width, height));
                context.getMessageRepository().create(imageMessage, "image", false, contentJson);
                System.out.println("Saved pending image message to local database - mediaId: " + mediaId + ", type: image");
                
                Contact current = context.getCurrentContactSupplier().get();
                if (current != null && current.getId() == contact.getId()) {
                    Platform.runLater(() -> context.getCurrentChatMessages().add(imageMessage));
                } else {
                    updateNotification(senderId);
                }
            } catch (Exception e) {
                System.err.println("Error al procesar ImageMessage pendiente: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private void processContactPresenceMessage(MessagesProto.ContactPresenceMessage message) {
        if (message.hasResponse()) {
            MessagesProto.ContactPresenceResponse response = message.getResponse();
            log.info("Recibida respuesta de presencia con {} contactos", response.getContactsCount());
            
            Platform.runLater(() -> {
                for (MessagesProto.ContactPresence contact : response.getContactsList()) {
                    context.getContactService().setContactOnlineByUsername(contact.getUsername(), contact.getOnline());
                    log.debug("Contacto {}: {}", contact.getUsername(), contact.getOnline() ? "ONLINE" : "OFFLINE");
                }
            });
        }
    }

    private void updateNotification(String senderUsername) {
        Platform.runLater(() -> Notification.updateOrAdd(context.getNotifications(), senderUsername));
    }

    /**
     * Programa el envío de confirmación de lectura con debounce.
     * Evita efecto rebote cuando llegan múltiples mensajes mientras el chat está abierto.
     * El timer se resetea con cada nuevo mensaje y solo envía la confirmación cuando expire.
     * 
     * Flujo: Llega mensaje → Resetear timer (3s) → Timer expira → 
     *        Obtener IDs no leídos → Marcar local → Enviar al servidor
     * 
     * @param senderId ID del usuario que envió los mensajes a marcar como leídos
     */
    void scheduleReadReceipt(String senderId) {
        log.info("[ReadReceipt] Mensaje recibido de {}, agendando confirmación de lectura con debounce", senderId);
        
        ScheduledFuture<?> existingTimer = readReceiptTimers.get(senderId);
        if (existingTimer != null && !existingTimer.isCancelled()) {
            existingTimer.cancel(false);
            log.debug("[ReadReceipt] Timer anterior cancelado para contacto: {}", senderId);
        }
        
        ScheduledFuture<?> newTimer = scheduler.schedule(() -> {
            executeReadReceipt(senderId);
        }, HttpConfig.READ_RECEIPT_DEBOUNCE_MS, TimeUnit.MILLISECONDS);
        
        readReceiptTimers.put(senderId, newTimer);
        log.debug("[ReadReceipt] Timer de {}ms iniciado para contacto: {}", 
                  HttpConfig.READ_RECEIPT_DEBOUNCE_MS, senderId);
    }

    /**
     * Ejecuta la confirmación de lectura: obtiene IDs no leídos, marca localmente y envía al servidor.
     * Se ejecuta cuando expira el timer de debounce.
     * 
     * @param senderId ID del usuario cuyos mensajes serán marcados como leídos
     */
private void executeReadReceipt(String senderId) {
        log.info("[ReadReceipt] Timer expirado, ejecutando confirmación de lectura para: {}", senderId);

        try {
            List<Long> unreadIds = context.getMessageRepository().getMessageIdsByStatus(senderId,
                    ChatMessage.MessageStatus.DELIVERED, ChatMessage.MessageStatus.SENT);

            if (unreadIds.isEmpty()) {
                log.debug("[ReadReceipt] No hay mensajes sin leer para: {}", senderId);
                readReceiptTimers.remove(senderId);
                return;
            }

            log.info("[ReadReceipt] {} mensajes sin leer encontrados para: {}", unreadIds.size(), senderId);

            context.getMessageRepository().updateMultipleStatus(unreadIds, ChatMessage.MessageStatus.READ);
            log.debug("[ReadReceipt] {} mensajes marcados como leídos localmente", unreadIds.size());

            String currentUserId = context.getCurrentUserIdSupplier().get();
            context.getMessageSender().sendMarkAsRead(currentUserId, senderId, unreadIds);

            log.info("[ReadReceipt] Confirmación de lectura enviada al servidor para {} mensajes", unreadIds.size());

            final List<Long> idsToUpdate = unreadIds;
            Platform.runLater(() -> {
                for (int i = 0; i < context.getCurrentChatMessages().size(); i++) {
                    ChatMessage msg = context.getCurrentChatMessages().get(i);
                    if (idsToUpdate.contains(msg.getId())) {
                        msg.setRead(true);
                        msg.setStatus(ChatMessage.MessageStatus.READ);
                        context.getCurrentChatMessages().set(i, msg);
                    }
                }
                if (context.getOnMessagesUpdated() != null) {
                    context.getOnMessagesUpdated().run();
                }
            });

        } catch (SQLException e) {
            log.error("[ReadReceipt] Error al ejecutar confirmación de lectura para {}: {}",
                    senderId, e.getMessage(), e);
        } finally {
            readReceiptTimers.remove(senderId);
        }
    }
}