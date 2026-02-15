package com.basic_chat.chat_service.handler;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;

import com.basic_chat.chat_service.context.SessionContext;
import com.basic_chat.chat_service.models.PendingUnblock;
import com.basic_chat.chat_service.repository.PendingUnblockRepository;
import com.basic_chat.chat_service.service.BlockService;
import com.basic_chat.chat_service.service.SessionManager;
import com.basic_chat.chat_service.service.RedisSessionService;
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
    private final RedisSessionService redisSessionService;
    private final PendingUnblockRepository pendingUnblockRepository;

    public UnblockContactHandler(BlockService blockService, SessionManager sessionManager, RedisSessionService redisSessionService, PendingUnblockRepository pendingUnblockRepository) {
        this.blockService = blockService;
        this.sessionManager = sessionManager;
        this.redisSessionService = redisSessionService;
        this.pendingUnblockRepository = pendingUnblockRepository;
    }

    @Override
    public boolean supports(WsMessage message) {
        return message.hasUnblockContactRequest();
    }

    /**
     * Procesa una solicitud de desbloqueo de contacto.
     * 
     * Flujo:
     * 1. Extrae el nombre de usuario del desbloqueador desde la sesión
     * 2. Obtiene el nombre del usuario a desbloquear desde la solicitud
     * 3. Ejecuta el desbloqueo mediante BlockService
     * 4. Si el usuario desbloqueado está online: envía notificación en tiempo real
     * 5. Si el usuario desbloqueado está offline: guarda en PendingUnblock para notificación posterior
     * 6. Envía respuesta al desbloqueador confirmando el éxito de la operación
     * 
     * @param context contexto de la sesión WebSocket del desbloqueador
     * @param message mensaje protobuf que contiene la solicitud de desbloqueo
     * @throws Exception si ocurre un error durante el procesamiento
     */
    @Override
    public void handle(SessionContext context, WsMessage message) throws Exception {
        UnblockContactRequest request = message.getUnblockContactRequest();
        String blocker = sessionManager.getSessionInfo(context.getSession().getId()).getUsername();
        String blocked = request.getRecipient();

        log.info("Usuario {} solicitando desbloquear a {}", blocker, blocked);

        boolean success = false;
        String responseMessage = "Usuario desbloqueado exitosamente";

        try {
            boolean blockResult = blockService.unblockUser(blocker, blocked);

            if(!blockResult){
               log.warn("BlockService retornó false para bloqueo de {} por {}", blocked, blocker);
                responseMessage = "No se pudo bloquear el usuario. Verifica que el nombre sea válido.";
                sendResponse(context, success, responseMessage);
                return;
            }
            success = true;
            responseMessage = "Usuario desbloqueado exitosamente";
            log.info("Desbloqueo exitoso: {} desbloqueó a {}", blocker, blocked);


            // Verificar si el usuario desbloqueado está en línea para notificarle
            if (redisSessionService.isUserOnlineByUsername(blocked)) {
                notifyUnBlockedUserIfOnline(blocked, blocker);
            } else {
                // Si está offline, guardar pendiente
                savePendingUnblockNotification(blocker, blocked);
            }
        } catch (Exception e) {
             log.error("Error al procesar solicitud de desbloqueo de {} a {}", blocker, blocked, e);
            responseMessage = "Error al desbloquear usuario: " + e.getMessage();
        }
        // Envia la respuesta al desbloqueador
        sendResponse(context, success, responseMessage);

    }

    /**
     * Notifica al usuario desbloqueado que ha sido desbloqueado (solo si está online).
     * 
     * Flujo:
     * 1. Busca la sesión WebSocket del usuario desbloqueado
     * 2. Si existe, envía una notificación en tiempo real
     * 3. Registra en logs el resultado de la notificación
     * 
     * @param blocked nombre del usuario que fue desbloqueado
     * @param blocker nombre del usuario que realizó el desbloqueo
     */
    private void notifyUnBlockedUserIfOnline(String blocked, String blocker){
        try {
            String sessionId = redisSessionService.getSessionIdByUsername(blocked);
            if (sessionId == null) {
                return;
            }
            SessionManager.SessionInfo blockedSession = sessionManager.getSessionInfo(sessionId);
            if (blockedSession != null) {
                    sendUnblockNotification(blockedSession.getWsSession(), blocker);
                    log.debug("Notificación de desbloqueo enviada a {}", blocked);
            }else{
                log.debug("La sesion del usuario desbloqueado {} no esta disponible. Guardando como pendiente.", blocked);
                savePendingUnblockNotification(blocker, blocked);
            }
        } catch (Exception e) {
            log.error("Error notificando al usuario desbloqueado {} del desbloqueo por {}", blocked, blocker, e);
            //Guardar como pendiente en caso de error
            savePendingUnblockNotification(blocker, blocked);
        }
    }

     /**
     * Guarda una notificación de desbloqueo pendiente en la base de datos.
     * 
     * Esta notificación será entregada al usuario cuando se conecte nuevamente.
     * Utiliza la tabla PendingUnblock para almacenar los desbloques que ocurrieron
     * mientras el usuario estaba offline.
     * 
     * @param blocker nombre del usuario que realizó el desbloqueo
     * @param blocked nombre del usuario que fue desbloqueado
     */
    private void savePendingUnblockNotification(String blocker, String blocked){
        try {
            // Crea un registro de desbloqueo pendiente
            PendingUnblock pendingUnblock = new PendingUnblock();
            pendingUnblock.setBlocker(blocker);
            pendingUnblock.setUnblockedUser(blocked);
            pendingUnblockRepository.save(pendingUnblock);
            log.info("Notificacion de bloqueo guardada como pendiente: {} desbloqueo a {}", blocker, blocked);
        } catch (Exception e) {
            log.error("Error al guardar desbloqueo pendiente en BD para {} desbloqueado por {}", blocked, blocker, e);
        }
    }

    /**
     * Envía una notificación de desbloqueo al usuario desbloqueado.
     * 
     * Crea un mensaje protobuf con la información del desbloqueador y lo envía
     * a través de la sesión WebSocket proporcionada.
     * 
     * @param session sesión WebSocket del usuario desbloqueado
     * @param blocker nombre del usuario que realizó el desbloqueo
     */
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

    /**
     * Envía la respuesta de la solicitud de desbloqueo al cliente desbloqueador.
     * 
     * @param context contexto de la sesión del desbloqueador
     * @param success indica si el desbloqueo fue exitoso
     * @param message mensaje descriptivo de la respuesta
     */
    private void sendResponse(SessionContext context, boolean success, String message){
        try {
            UnblockContactResponse response = UnblockContactResponse.newBuilder()
            .setSuccess(success)
            .setMessage(message)
            .build();

            WsMessage wsResponse = WsMessage.newBuilder()
                .setUnblockContactResponse(response)
                .build();

            context.getSession().sendMessage(new BinaryMessage(wsResponse.toByteArray()));
            log.info("Respuesta de desbloqueo enviada al desbloqueador - Éxito: {}", success);
        } catch (Exception e) {
            log.error("Error enviando respuesta de desbloqueo al cliente", e);
        }
    }
}
