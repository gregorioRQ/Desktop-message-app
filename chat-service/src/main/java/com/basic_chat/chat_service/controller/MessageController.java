package com.basic_chat.chat_service.controller;

import com.basic_chat.chat_service.service.MessageService;
import com.basic_chat.proto.MessagesProto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Map;

/**
 * Controlador REST para gestionar mensajes y operaciones relacionadas.
 * 
 * Este controlador proporciona endpoints para que connection-service obtenga
 * los mensajes pendientes de un usuario cuando se conecta. Anteriormente,
 * solo devolvía mensajes de chat no leídos, pero ahora devuelve TODOS los
 * tipos de pendientes en un solo response para optimizar la conexión inicial.
 * 
 * Endpoints disponibles:
 * - GET /api/v1/messages/pending/{username}: Obtiene todos los mensajes pendientes
 * - GET /api/v1/messages/{messageId}/recipient: Obtiene el destinatario de un mensaje
 * 
 * El endpoint principal (/pending) devuelve un WsMessage que puede contener:
 * - Mensajes de chat (UnreadMessagesList)
 * - Notificaciones de eliminación (MessageDeletedNotification en PendingClearHistoryList)
 * - Lista de bloqueos (BlockedUsersList)
 * - Lista de desbloqueos (UnblockedUsersList)
 * - Historial limpiado (PendingClearHistoryList)
 * - Confirmaciones de lectura (MessagesReadUpdate)
 * - Identidades de contacto (ContactIdentity)
 */
@RestController
@RequestMapping("/api/v1/messages")
@Slf4j
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Obtiene todos los mensajes pendientes para un usuario.
     * 
     * Este endpoint es llamado por connection-service cuando un usuario se conecta.
     * Devuelve TODOS los tipos de mensajes pendientes en un solo WsMessage,
     * incluyendo mensajes de chat, eliminaciones, bloqueos, desbloqueos,
     * limpiezas de historial, confirmaciones de lectura e identidades de contacto.
     * 
     * Una vez entregados al cliente, todos los pendientes se eliminan del servidor.
     * 
     * @param username Nombre del usuario que solicita sus mensajes pendientes
     * @return Response con el WsMessage codificado en Base64 o error
     */
    @GetMapping("/pending/{username}")
    public ResponseEntity<Map<String, Object>> getPendingMessages(@PathVariable String username) {
        try {
            log.info("Solicitud de mensajes pendientes para usuario: {}", username);
            
            // Obtiene TODOS los mensajes pendientes en un solo WsMessage
            MessagesProto.WsMessage wsMessage = messageService.getAllPendingMessages(username);

            if (wsMessage == null) {
                log.info("No hay mensajes pendientes para usuario: {}", username);
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "count", 0,
                        "hasMore", false,
                        "messages", java.util.List.of()
                ));
            }

            // Convertir el WsMessage a Base64 para retornarlo como JSON
            String encodedMessage = Base64.getEncoder().encodeToString(wsMessage.toByteArray());
            
            // Calcular una estimación del "count" basado en los campos presentes
            int count = 0;
            if (wsMessage.hasUnreadMessagesList()) {
                count += wsMessage.getUnreadMessagesList().getMessagesCount();
            }
            if (wsMessage.hasBlockedUsersList()) {
                count += wsMessage.getBlockedUsersList().getUsersCount();
            }
            if (wsMessage.hasUnblockedUsersList()) {
                count += wsMessage.getUnblockedUsersList().getUsersCount();
            }
            
            log.info("Enviando {} elementos pendientes para usuario: {}", count, username);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "count", count,
                    "hasMore", false,
                    "messages", java.util.List.of(encodedMessage)
            ));
        } catch (Exception e) {
            log.error("Error al obtener mensajes pendientes para usuario {}: {}", username, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * Obtiene el destinatario de un mensaje específico.
     * 
     * Este endpoint es usado por connection-service para determinar a quién notificar
     * cuando se procesa una solicitud de eliminación de mensaje.
     * 
     * @param messageId ID del mensaje
     * @return Nombre de usuario del destinatario
     
    @GetMapping("/{messageId}/recipient")
    public ResponseEntity<Map<String, Object>> getMessageRecipient(@PathVariable String messageId) {
        try {
            String recipient = messageService.getMessageRecipient(messageId);
            
            if (recipient == null) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "recipient", ""
                ));
            }
            
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "recipient", recipient
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "error", e.getMessage()));
        }
    }*/
}
