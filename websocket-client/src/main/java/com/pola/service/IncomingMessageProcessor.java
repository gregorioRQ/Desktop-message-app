package com.pola.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pola.model.ChatMessage;
import com.pola.model.Contact;
import com.pola.model.Notification;
import com.pola.proto.MessagesProto;
import com.pola.proto.MessagesProto.WsMessage;
import com.pola.proto.MessagesProto.PendingClearHistoryList;
import com.pola.util.MessageProcessingContext;
import javafx.application.Platform;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Procesa los mensajes entrantes del WebSocket.
 * Principio SOLID: Open/Closed - Fácil de extender con nuevos handlers en el mapa.
 */
public class IncomingMessageProcessor {
    private static final Logger log = LoggerFactory.getLogger(IncomingMessageProcessor.class);
    
    private final MessageProcessingContext context;
    private final Map<WsMessage.PayloadCase, Consumer<WsMessage>> handlers = new HashMap<>();
    private Consumer<String> errorListener;

    public IncomingMessageProcessor(MessageProcessingContext context) {
        this.context = context;
        initializeHandlers();
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
          handlers.put(WsMessage.PayloadCase.CHAT_MESSAGE_RESPONSE, this::handleMessageError);
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
          handlers.put(WsMessage.PayloadCase.CONTACT_IDENTITY, msg -> processContactIdentity(msg.getContactIdentity()));
          // Handlers para solicitudes de bloqueo y desbloqueo de contactos
          handlers.put(WsMessage.PayloadCase.BLOCK_CONTACT_REQUEST, this::processBlockContactRequest);
          handlers.put(WsMessage.PayloadCase.UNBLOCK_CONTACT_REQUEST, this::processUnblockContactRequest);
      }

    public void process(WsMessage message) {
        Consumer<WsMessage> handler = handlers.get(message.getPayloadCase());
        if (handler != null) {
            handler.accept(message);
        } else {
            System.out.println("Tipo de mensaje no manejado: " + message.getPayloadCase());
        }
    }

    private void handleMessageError(WsMessage wsMessage) {
        MessagesProto.ChatMessageResponse response = wsMessage.getChatMessageResponse();
        String errorContent = response.getErrorMessage();
        
        if (response.getCause() == MessagesProto.FailureCause.BLOCKED) {
            String recipient = response.getRecipient();
            context.getContactService().markUserAsBlockingMe(recipient);

            try {
                if (response.getMessageId() != null && !response.getMessageId().isEmpty()) {
                    long msgId = Long.parseLong(response.getMessageId());
                    context.getMessageRepository().delete(msgId);
                    Platform.runLater(() -> context.getCurrentChatMessages().removeIf(m -> m.getId() == msgId));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            Contact current = context.getCurrentContactSupplier().get();
            if (current != null && current.getContactUsername().equals(recipient)) {
                Platform.runLater(() -> {
                    ChatMessage systemMessage = new ChatMessage(recipient, "Sistema", errorContent, "Sistema");
                    systemMessage.setId(System.currentTimeMillis());
                    context.getCurrentChatMessages().add(systemMessage);
                });
            }
        } else if (errorListener != null) {
            Platform.runLater(() -> errorListener.accept(errorContent));
        }
    }

    private void handleChatMessage(WsMessage wsMessage) {
        MessagesProto.ChatMessage protobufMessage = wsMessage.getChatMessage();
        String senderId = protobufMessage.getSender();
        String content = protobufMessage.getContent();
        long messageId = Long.parseLong(protobufMessage.getId());
      
        try {
            if (context.getMessageRepository().existsById(messageId)) return;

            Contact contact = context.getContactService().findContactByUsername(context.getCurrentUserIdSupplier().get(), senderId)
            //añade un contacto "improvisado" con un id no oficial
                .orElseGet(() -> context.getContactService().addContact(context.getCurrentUserIdSupplier().get(), senderId, false));

            if(contact == null) return;

            ChatMessage localMessage = new ChatMessage(contact.getContactUsername(), senderId, content, senderId);
            localMessage.setId(messageId);
            ChatMessage saved = context.getMessageRepository().create(localMessage);

            Contact current = context.getCurrentContactSupplier().get();
            if(current != null && current.getId() == contact.getId()){
                Platform.runLater(() -> context.getCurrentChatMessages().add(saved));
                context.getMessageRepository().markAsRead(saved.getId());
            } else {
                updateNotification(senderId);
            }
        } catch (SQLException e) {
            e.printStackTrace();
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
        for (MessagesProto.ChatMessage protoMessage : unreadMessagesList.getMessagesList()) {
            String senderUsername = protoMessage.getSender();
            long messageId = Long.parseLong(protoMessage.getId());

            try {
                Contact contact = context.getContactService().findContactByUsername(context.getCurrentUserIdSupplier().get(), senderUsername)
                        .orElseGet(() -> context.getContactService().addContact(context.getCurrentUserIdSupplier().get(), senderUsername, false));

                if (contact == null || context.getMessageRepository().existsById(messageId)) continue;

                ChatMessage localMessage = new ChatMessage(contact.getContactUsername(), senderUsername, protoMessage.getContent(), senderUsername);
                localMessage.setId(messageId);
                ChatMessage saved = context.getMessageRepository().create(localMessage);

                Contact current = context.getCurrentContactSupplier().get();
                if (current != null && current.getId() == contact.getId()) {
                    Platform.runLater(() -> context.getCurrentChatMessages().add(saved));
                    context.getMessageRepository().markAsRead(saved.getId());
                } else {
                    updateNotification(senderUsername);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void processMessagesReadUpdate(MessagesProto.MessagesReadUpdate update) {
        List<String> idsStr = update.getMessageIdsList();
        if (idsStr.isEmpty()) return;

        List<Long> ids = new java.util.ArrayList<>();
        for(String s : idsStr) {
            try { ids.add(Long.parseLong(s)); } catch (NumberFormatException e) {}
        }

        try {
            context.getMessageRepository().markMultipleAsRead(ids);
            Platform.runLater(() -> {
                for (int i = 0; i < context.getCurrentChatMessages().size(); i++) {
                    ChatMessage msg = context.getCurrentChatMessages().get(i);
                    if (ids.contains(msg.getId())) {
                        msg.setRead(true);
                        context.getCurrentChatMessages().set(i, msg);
                    }
                }
            });
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
    /**
     * Actualizara el id temporal por el id oficial del remitente.
     * @param identity El .proto con el id del remitente y su username
     */
    private void processContactIdentity(MessagesProto.ContactIdentity identity) {
        String remoteUserId = identity.getSenderId();
        String senderUsername = identity.getSenderUsername();

        if(senderUsername != null && !senderUsername.isEmpty()){
            // Verificar si es la primera vez que obtenemos el ID de este contacto (era null o vacío)
            boolean isFirstIdUpdate = context.getContactService().findContactByUsername(context.getCurrentUserIdSupplier().get(), senderUsername)
                .map(c -> c.getContactUserId() == null || c.getContactUserId().isEmpty())
                .orElse(true);

            context.getContactService().updateContactId(senderUsername, remoteUserId);
            
            // Marcar como conectado inmediatamente ya que acabamos de recibir señal de vida
            context.getContactService().setContactOnline(remoteUserId, true);

            // Si es la primera vez que tenemos su ID, enviamos el nuestro de vuelta para completar el handshake
            if (isFirstIdUpdate) {
                context.getMessageSender().sendContactIdentity(context.getCurrentUserIdSupplier().get(), context.getCurrentUsernameSupplier().get(), senderUsername);
            }

            // Añadir notificación a la bandeja en lugar de mensaje de chat
            updateNotification(senderUsername);
        }
        // Si el contacto era "improvisado" (sin ID), ahora tiene ID.
        // Devolvemos nuestro ID para completar el handshake si es necesario.
        // if (contactWasTemporary) {
        //     messageSender.sendContactIdentity(senderUsername, currentUserIdSupplier.get());
        // }
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

    private void updateNotification(String senderUsername) {
        Platform.runLater(() -> Notification.updateOrAdd(context.getNotifications(), senderUsername));
    }
}