package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.PendingBlock;
import com.basic_chat.chat_service.models.PendingUnblock;
import com.basic_chat.chat_service.repository.PendingBlockRepository;
import com.basic_chat.chat_service.repository.PendingUnblockRepository;
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
    private final PendingBlockRepository pendingBlockRepository;

    public BlockContactHandler(BlockService blockService, 
                              SessionManager sessionManager,
                              PendingBlockRepository pendingBlockRepository) {
        this.blockService = blockService;
        this.sessionManager = sessionManager;
        this.pendingBlockRepository = pendingBlockRepository;
    }

    @Override
    public boolean supports(WsMessage message) {
        return message.hasBlockContactRequest();
    }

    /**
     * Procesa una solicitud de bloqueo de contacto.
     * 
     * Flujo:
     * 1. Extrae el nombre de usuario del bloqueador desde la sesión
     * 2. Obtiene el nombre del usuario a bloquear desde la solicitud
     * 3. Ejecuta el bloqueo mediante BlockService
     * 4. Si el usuario bloqueado está online: envía notificación en tiempo real
     * 5. Si el usuario bloqueado está offline: guarda en PendingUnblock para notificación posterior
     * 6. Envía respuesta al bloqueador confirmando el éxito de la operación
     * 
     * @param context contexto de la sesión WebSocket del bloqueador
     * @param message mensaje protobuf que contiene la solicitud de bloqueo
     * @throws Exception si ocurre un error durante el procesamiento
     */
    @Override
    public void handle(SessionContext context, WsMessage message) throws Exception {
        BlockContactRequest request = message.getBlockContactRequest();
        
        // Extrae el nombre del usuario que realiza el bloqueo desde la sesión autenticada
        String blocker = sessionManager.getSessionInfo(context.getSession().getId()).getUsername();
        String blocked = request.getRecipient();

        log.info("Usuario {} solicitando bloquear a {}", blocker, blocked);

        boolean success = false;
        String responseMessage = "Error desconocido al bloquear usuario";

        try {
            // Intenta bloquear el usuario mediante el servicio de bloqueo
            boolean blockResult = blockService.blockUser(blocker, blocked);
            
            if (!blockResult) {
                log.warn("BlockService retornó false para bloqueo de {} por {}", blocked, blocker);
                responseMessage = "No se pudo bloquear el usuario. Verifica que el nombre sea válido.";
                sendResponse(context, success, responseMessage);
                return;
            }

            success = true;
            responseMessage = "Usuario bloqueado exitosamente";
            log.info("Bloqueo exitoso: {} bloqueó a {}", blocker, blocked);

            // Verifica si el usuario bloqueado está conectado en este momento
            if (sessionManager.isUserOnline(blocked)) {
                notifyBlockedUserIfOnline(blocked, blocker);
            } else {
                // Si el usuario está offline, guarda el bloqueo como pendiente para notificación posterior
                savePendingBlockNotification(blocker, blocked);
            }

        } catch (Exception e) {
            log.error("Error al procesar solicitud de bloqueo de {} a {}", blocker, blocked, e);
            responseMessage = "Error al bloquear usuario: " + e.getMessage();
        }

        // Envía la respuesta al bloqueador
        sendResponse(context, success, responseMessage);
    }

    /**
     * Notifica al usuario bloqueado que ha sido bloqueado (solo si está online).
     * 
     * Flujo:
     * 1. Busca la sesión WebSocket del usuario bloqueado
     * 2. Si existe, envía una notificación en tiempo real
     * 3. Registra en logs el resultado de la notificación
     * 
     * @param blocked nombre del usuario que fue bloqueado
     * @param blocker nombre del usuario que realizó el bloqueo
     */
    private void notifyBlockedUserIfOnline(String blocked, String blocker) {
        try {
            SessionManager.SessionInfo blockedSession = sessionManager.findByUsername(blocked);
            
            if (blockedSession != null && blockedSession.getWsSession().isOpen()) {
                sendBlockNotification(blockedSession.getWsSession(), blocker);
                log.debug("Notificación de bloqueo en tiempo real enviada a {} desde {}", blocked, blocker);
            } else {
                log.debug("La sesión del usuario bloqueado {} no está disponible. Guardando como pendiente.", blocked);
                savePendingBlockNotification(blocker, blocked);
            }
        } catch (Exception e) {
            log.error("Error notificando al usuario bloqueado {} del bloqueo por {}", blocked, blocker, e);
            // Guardamos como pendiente en caso de error
            savePendingBlockNotification(blocker, blocked);
        }
    }

    /**
     * Guarda una notificación de bloqueo pendiente en la base de datos.
     * 
     * Esta notificación será entregada al usuario cuando se conecte nuevamente.
     * Utiliza la tabla PendingUnblock para almacenar los bloques que ocurrieron
     * mientras el usuario estaba offline.
     * 
     * @param blocker nombre del usuario que realizó el bloqueo
     * @param blocked nombre del usuario que fue bloqueado
     */
    private void savePendingBlockNotification(String blocker, String blocked) {
        try {
            // Crea un registro de bloqueo pendiente
            PendingBlock pendingBlock = new PendingBlock();
            pendingBlock.setBlocker(blocker);
            pendingBlock.setBlockedUser(blocked);

            pendingBlockRepository.save(pendingBlock);
            log.info("Notificación de bloqueo guardada como pendiente: {} bloqueó a {}", blocker, blocked);
        } catch (Exception e) {
            log.error("Error al guardar bloqueo pendiente en BD para {} bloqueado por {}", blocked, blocker, e);
        }
    }

    /**
     * Envía una notificación de bloqueo al usuario bloqueado.
     * 
     * Crea un mensaje protobuf con la información del bloqueador y lo envía
     * a través de la sesión WebSocket proporcionada.
     * 
     * @param session sesión WebSocket del usuario bloqueado
     * @param blocker nombre del usuario que realizó el bloqueo
     */
    private void sendBlockNotification(org.springframework.web.socket.WebSocketSession session, String blocker) {
        try {
            // Construye un mensaje protobuf con la lista de usuarios que bloquearon
            MessagesProto.BlockedUsersList list = MessagesProto.BlockedUsersList.newBuilder()
                    .addUsers(blocker)
                    .build();

            WsMessage wsMessage = WsMessage.newBuilder()
                    .setBlockedUsersList(list)
                    .build();
            
            session.sendMessage(new BinaryMessage(wsMessage.toByteArray()));
            log.debug("Notificación de bloqueo enviada exitosamente a través de WebSocket");
        } catch (Exception e) {
            log.error("Error enviando notificación de bloqueo a través de WebSocket", e);
        }
    }

    /**
     * Envía la respuesta de la solicitud de bloqueo al cliente bloqueador.
     * 
     * @param context contexto de la sesión del bloqueador
     * @param success indica si el bloqueo fue exitoso
     * @param message mensaje descriptivo de la respuesta
     */
    private void sendResponse(SessionContext context, boolean success, String message) {
        try {
            BlockContactResponse response = BlockContactResponse.newBuilder()
                    .setSuccess(success)
                    .setMessage(message)
                    .build();

            WsMessage wsResponse = WsMessage.newBuilder()
                    .setBlockContactResponse(response)
                    .build();

            context.getSession().sendMessage(new BinaryMessage(wsResponse.toByteArray()));
            log.info("Respuesta de bloqueo enviada al bloqueador - Éxito: {}", success);
        } catch (Exception e) {
            log.error("Error enviando respuesta de bloqueo al cliente", e);
        }
    }
}
