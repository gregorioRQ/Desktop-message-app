package com.basic_chat.notifiation_service.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.basic_chat.notifiation_service.model.ContactUser;
import com.basic_chat.notifiation_service.model.User;
import com.basic_chat.notifiation_service.repository.ContactUserRepository;
import com.basic_chat.notifiation_service.repository.UserRepository;
import com.basic_chat.notifiation_service.service.SseNotificationService;
import com.basic_chat.proto.MessagesProto;

/**
 * Controlador REST para gestionar la presencia de usuarios.
 * 
 * Este controlador proporciona endpoints para:
 * - Consultar el estado de los contactos del usuario
 * - Notificar cuando un usuario se conecta (POST /online)
 * - Notificar cuando un usuario se desconecta (POST /offline)
 * 
 * El sistema de presencia usa la tabla 'users' con campo 'online' boolean
 * para almacenar el estado. Los contactos se filtran usando 'contact_users'.
 * donde solo se consideran los contactos mutuamente confirmados.
 */
@RestController
@RequestMapping("/api/presence")
public class PresenceController {

    private static final Logger log = LoggerFactory.getLogger(PresenceController.class);

    private static final String PRESENCE_EVENT_NAME = "presence";
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final long STREAM_TIMEOUT_HOURS = 1;

    private final SseNotificationService sseNotificationService;
    private final UserRepository userRepository;
    private final ContactUserRepository contactUserRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public PresenceController(
            SseNotificationService sseNotificationService,
            UserRepository userRepository,
            ContactUserRepository contactUserRepository) {
        this.sseNotificationService = sseNotificationService;
        this.userRepository = userRepository;
        this.contactUserRepository = contactUserRepository;
    }

    /**
     * Actualiza el estado de un usuario a ONLINE.
     * 
     * Este endpoint es llamado por connection-service cuando un usuario se conecta.
     * 
     * @param request Datos del usuario que se conectó
     * @return ResponseEntity con estado de la operación
     */
    @PostMapping("/online")
    public ResponseEntity<?> setUserOnline(@RequestBody PresenceUpdateRequest request) {
        log.info("Notificación de usuario ONLINE: {}", request.getUserId());

        try {
            Optional<User> userOpt = userRepository.findById(request.getUserId());
            
            if (userOpt.isEmpty()) {
                log.warn("Usuario no encontrado: {}", request.getUserId());
                return ResponseEntity.notFound().build();
            }

            User user = userOpt.get();
            user.setOnline(true);
            userRepository.save(user);

            log.info("Usuario {} marcado como ONLINE en la base de datos", request.getUserId());

            // Notificar a los contactos interesados mediante SSE
            notifyInterestedContacts(request.getUserId(), "ONLINE", request.getUsername());

            return ResponseEntity.ok("{\"status\": \"success\"}");
        } catch (Exception e) {
            log.error("Error al actualizar estado ONLINE: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Actualiza el estado de un usuario a OFFLINE.
     * 
     * Este endpoint es llamado por connection-service cuando un usuario se desconecta.
     * 
     * @param request Datos del usuario que se desconectó
     * @return ResponseEntity con estado de la operación
     */
    @PostMapping("/offline")
    public ResponseEntity<?> setUserOffline(@RequestBody PresenceUpdateRequest request) {
        log.info("Notificación de usuario OFFLINE: {}", request.getUserId());

        try {
            Optional<User> userOpt = userRepository.findById(request.getUserId());
            
            if (userOpt.isEmpty()) {
                log.warn("Usuario no encontrado: {}", request.getUserId());
                return ResponseEntity.notFound().build();
            }

            User user = userOpt.get();
            user.setOnline(false);
            userRepository.save(user);

            log.info("Usuario {} marcado como OFFLINE en la base de datos", request.getUserId());

            // Notificar a los contactos interesados mediante SSE
            String username = request.getUsername() != null ? request.getUsername() : user.getUsername();
            notifyInterestedContacts(request.getUserId(), "OFFLINE", username);

            return ResponseEntity.ok("{\"status\": \"success\"}");
        } catch (Exception e) {
            log.error("Error al actualizar estado OFFLINE: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Consulta el estado de los contactos confirmados del usuario.
     * 
     * Este endpoint devuelve una lista de contactos con su estado online/offline.
     * Solo devuelve contactos que tienen is_confirmed = true.
     * 
     * @param userId El ID del usuario que solicita sus contactos
     * @return Lista de contactos con su estado en formato protobuf
     */
    @GetMapping(value = "/contacts", produces = "application/x-protobuf")
    public ResponseEntity<byte[]> getContactsPresence(
            @RequestHeader("X-User-Id") String userId) {
        log.info("Solicitud de contactos confirmados para usuario: {}", userId);

        try {
            List<ContactUser> contacts = contactUserRepository.findByUserId(userId);
            
            MessagesProto.ContactPresenceResponse.Builder responseBuilder = MessagesProto.ContactPresenceResponse.newBuilder();
            
            for (ContactUser contact : contacts) {
                Optional<User> contactUserOpt = userRepository.findByUsername(contact.getContactUsername());
                
                if (contactUserOpt.isPresent()) {
                    User contactUser = contactUserOpt.get();
                    MessagesProto.ContactPresence contactPresence = MessagesProto.ContactPresence.newBuilder()
                        .setUsername(contact.getContactUsername())
                        .setUserId(contactUser.getId())
                        .setOnline(contactUser.isOnline())
                        .build();
                    responseBuilder.addContacts(contactPresence);
                }
            }

            MessagesProto.ContactPresenceResponse response = responseBuilder.build();
            log.info("Devolviendo {} contactos para usuario: {}", response.getContactsCount(), userId);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/x-protobuf"));
            return new ResponseEntity<>(response.toByteArray(), headers, org.springframework.http.HttpStatus.OK);
        } catch (Exception e) {
            log.error("Error al obtener contactos: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Notifica a los contactos interesados sobre un cambio de estado.
     * 
     * @param userId ID del usuario que cambió su estado
     * @param type Tipo de cambio (ONLINE/OFFLINE)
     * @param username Username del usuario
     */
    private void notifyInterestedContacts(String userId, String type, String username) {
        // Buscar usuarios que tienen a este username como contacto
        List<ContactUser> interestedUsers = contactUserRepository.findByContactUsername(username);

        log.info("Notificando a {} usuarios interesados sobre {} de {}", 
                interestedUsers.size(), type, username);

        for (ContactUser contact : interestedUsers) {
            String interestedUserId = contact.getUserId();
            
            if (sseNotificationService.hasActiveConnection(interestedUserId)) {
                String eventJson = String.format(
                        "{\"type\":\"%s\",\"userId\":\"%s\",\"username\":\"%s\"}",
                        type, userId, username
                );

                try {
                    SseEmitter emitter = sseNotificationService.getEmitter(interestedUserId);
                    if (emitter != null) {
                        emitter.send(SseEmitter.event()
                                .name("presence")
                                .data(eventJson));
                        log.info("Notificación de presencia enviada a usuario: {} ({})", 
                                interestedUserId, type);
                    }
                } catch (Exception e) {
                    log.warn("Error al enviar notificación a usuario {}: {}", 
                            interestedUserId, e.getMessage());
                }
            } else {
                log.debug("Usuario {} no tiene conexión SSE activa", interestedUserId);
            }
        }
    }

    // DTOs

    public static class PresenceUpdateRequest {
        private String userId;
        private String username;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }

    public static class ContactPresenceResponse {
        private String username;
        private String userId;
        private String status;

        public ContactPresenceResponse(String username, String userId, String status) {
            this.username = username;
            this.userId = userId;
            this.status = status;
        }

        public String getUsername() { return username; }
        public String getUserId() { return userId; }
        public String getStatus() { return status; }
    }
}