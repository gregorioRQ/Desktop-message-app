package com.basic_chat.notifiation_service.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.basic_chat.notifiation_service.repository.ContactUserRepository;
import com.basic_chat.notifiation_service.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class SseNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(SseNotificationService.class);

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ContactUserRepository contactUserRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SseNotificationService(ContactUserRepository contactUserRepository, UserRepository userRepository) {
        this.contactUserRepository = contactUserRepository;
        this.userRepository = userRepository;
    }

    /**
    * Registra un nuevo cliente SSE para un usuario específico.
    *
    * Este método almacena el SseEmitter para permitir enviar eventos al cliente conectado.
    * Después de registrar al cliente, automáticamente envía la lista de contactos online
    * para que el cliente sincronice su estado inicial.
    *
    * Flujo:
    * 1. Almacena el emitter en el mapa (usando username como clave)
    * 2. Marca al usuario como ONLINE en la base de datos
    * 3. Envía la lista de contactos online al cliente
    * 4. Notifica a los contactos del usuario que está ONLINE
    *
    * @param username El username del usuario que se suscribe
    * @param emitter El SseEmitter a usar para este cliente
    */
    public void registerClient(String username, SseEmitter emitter) {
        logger.info("[SseNotificationService] Registrando cliente SSE para usuario: {}", username);

        // Almacenar el emitter usando username como clave
        emitters.put(username, emitter);
        logger.debug("[SseNotificationService] Cliente SSE registrado. Total clientes: {}", emitters.size());

        // Marcar usuario como ONLINE
        markUserOnline(username);

        // Enviar lista de contactos online al cliente
        sendContactsList(username);

        // Notificar a los contactos que este usuario está ONLINE
        notifyContactsOfPresence(username, "ONLINE");

        logger.info("[SseNotificationService] Registro completado para usuario: {}. Contactos notificados.", username);
    }

    /**
     * Marca a un usuario como ONLINE en la base de datos.
     *
     * Este método actualiza el estado del usuario en la tabla users,
     * permitiendo que otros usuarios vean que está conectado.
     *
     * @param username Username del usuario a marcar como online
     */
    private void markUserOnline(String username) {
        try {
            userRepository.findByUsername(username).ifPresent(user -> {
                user.setOnline(true);
                userRepository.save(user);
                logger.info("[SseNotificationService] Usuario {} marcado como ONLINE en BD", username);
            });
        } catch (Exception e) {
            logger.error("[SseNotificationService] Error al marcar usuario {} como ONLINE: {}", username, e.getMessage());
        }
    }

    /**
     * Marca a un usuario como OFFLINE en la base de datos.
     *
     * Este método actualiza el estado del usuario cuando se detecta
     * que su conexión SSE se ha cerrado.
     *
     * @param username Username del usuario a marcar como offline
     */
    private void markUserOffline(String username) {
        try {
            userRepository.findByUsername(username).ifPresent(user -> {
                user.setOnline(false);
                userRepository.save(user);
                logger.info("[SseNotificationService] Usuario {} marcado como OFFLINE en BD", username);
            });
        } catch (Exception e) {
            logger.error("[SseNotificationService] Error al marcar usuario {} como OFFLINE: {}", username, e.getMessage());
        }
    }

    /**
     * Envía la lista de contactos online al usuario especificado.
     *
     * Este método consulta la base de datos para obtener los contactos del usuario
     * que están actualmente online, y envía un evento SSE con nombre "contact_list"
     * conteniendo solo los contactos online.
     *
     * Formato del evento:
     * event: contact_list
     * data: {"contacts":[{"userId":"abc","username":"john"},...]}
     *
     * @param username Username del usuario al que enviar la lista
     */
    public void sendContactsList(String username) {
        SseEmitter emitter = emitters.get(username);
        if (emitter == null) {
            logger.warn("[SseNotificationService] No se puede enviar lista de contactos - cliente no conectado: {}", username);
            return;
        }

        try {
            // Obtener contactos online del usuario
            List<String> onlineContactUsernames = contactUserRepository.findOnlineContactUsernamesByUsername(username);
            logger.info("[SseNotificationService] Enviando lista de {} contactos online a usuario: {}",
                onlineContactUsernames.size(), username);

            // Construir JSON con la lista de contactos
            ObjectNode rootNode = objectMapper.createObjectNode();
            ArrayNode contactsArray = rootNode.putArray("contacts");

            for (String contactUsername : onlineContactUsernames) {
                // Buscar el userId del contacto
                userRepository.findByUsername(contactUsername).ifPresent(contactUser -> {
                    ObjectNode contactNode = contactsArray.addObject();
                    contactNode.put("userId", contactUser.getId());
                    contactNode.put("username", contactUsername);
                });
            }

            String jsonPayload = objectMapper.writeValueAsString(rootNode);

            // Enviar evento SSE con nombre "contact_list"
            emitter.send(SseEmitter.event()
                .name("contact_list")
                .data(jsonPayload));

            logger.debug("[SseNotificationService] Lista de contactos enviada exitosamente a usuario: {}", username);

        } catch (Exception e) {
            logger.error("[SseNotificationService] Error al enviar lista de contactos a usuario {}: {}", username, e.getMessage());
        }
    }

    /**
     * Notifica a los contactos de un usuario sobre su cambio de presencia.
     *
     * Este método encuentra todos los contactos del usuario y les envía
     * un evento SSE con nombre "presence" indicando si el usuario está
     * ONLINE u OFFLINE.
     *
     * @param username Username del usuario que cambió de estado
     * @param type "ONLINE" o "OFFLINE"
     */
    private void notifyContactsOfPresence(String username, String type) {
        try {
            // Buscar información del usuario por username
            var userOpt = userRepository.findByUsername(username);
            if (userOpt.isEmpty()) {
                logger.warn("[SseNotificationService] Usuario {} no encontrado para notificar presencia", username);
                return;
            }

            var user = userOpt.get();
            String userId = user.getId();

            // Buscar contactos del usuario (usuarios que tienen a este usuario como contacto)
            var interestedContacts = contactUserRepository.findByContactUsername(username);
            logger.info("[SseNotificationService] Notificando {} contactos sobre {} de usuario: {}",
                interestedContacts.size(), type, username);

            // Construir JSON del evento
            ObjectNode eventNode = objectMapper.createObjectNode();
            eventNode.put("type", type);
            eventNode.put("userId", userId);
            eventNode.put("username", username);
            String jsonPayload = objectMapper.writeValueAsString(eventNode);

            // Enviar a cada contacto que esté conectado
            for (var contact : interestedContacts) {
                String contactUsername = contact.getUsername();
                SseEmitter contactEmitter = emitters.get(contactUsername);

                if (contactEmitter != null) {
                    try {
                        contactEmitter.send(SseEmitter.event()
                            .name("presence")
                            .data(jsonPayload));
                        logger.debug("[SseNotificationService] Notificación {} enviada a contacto: {}", type, contactUsername);
                    } catch (IOException e) {
                        logger.warn("[SseNotificationService] Error al notificar a contacto {}: {}", contactUsername, e.getMessage());
                    }
                }
            }

        } catch (Exception e) {
            logger.error("[SseNotificationService] Error al notificar presencia de usuario {}: {}", username, e.getMessage());
        }
    }

    /**
     * Desregistra un cliente SSE cuando se desconecta.
     *
     * Este método limpia los recursos del emitter y realiza las acciones necesarias
     * al detectar una desconexión:
     * 1. Marca al usuario como OFFLINE en la base de datos
     * 2. Notifica a los contactos que el usuario se ha desconectado
     *
     * @param username El username del usuario a desregistrar
     */
    public void unregisterClient(String username) {
        SseEmitter removed = emitters.remove(username);
        if (removed != null) {
            try {
                removed.complete();
            } catch (Exception e) {
                logger.debug("[SseNotificationService] Error al completar emitter para usuario: {}", username, e);
            }

            // Marcar usuario como OFFLINE
            markUserOffline(username);

            // Notificar a los contactos que el usuario está OFFLINE
            notifyContactsOfPresence(username, "OFFLINE");

            logger.info("[SseNotificationService] Cliente SSE desregistrado para usuario: {}. Clientes restantes: {}",
                username, emitters.size());
        } else {
            logger.warn("[SseNotificationService] Intentó desregistrar cliente inexistente para usuario: {}", username);
        }
    }

    /**
     * Envía una notificación simple a un usuario específico vía SSE.
     *
     * Este método empuja un mensaje al stream SSE del usuario.
     * El mensaje se enviará como un evento SSE con el nombre por defecto.
     *
     * @param username El username del usuario destinatario
     * @param message El mensaje de notificación a enviar
     * @return true si el mensaje fue enviado exitosamente, false en caso contrario
     */
    public boolean sendNotification(String username, String message) {
        SseEmitter emitter = emitters.get(username);

        if (emitter == null) {
            logger.warn("[SseNotificationService] No se puede enviar notificación - cliente no encontrado: {}", username);
            return false;
        }

        try {
            emitter.send(message);
            logger.debug("[SseNotificationService] Notificación enviada exitosamente a usuario: {}", username);
            return true;
        } catch (IOException e) {
            logger.error("[SseNotificationService] Error al enviar notificación a usuario {}: {}", username, e.getMessage());
            unregisterClient(username);
            return false;
        }
    }

    /**
     * Verifica si un usuario tiene una conexión SSE activa.
     *
     * @param username El username del usuario a verificar
     * @return true si el usuario tiene una conexión SSE activa, false en caso contrario
     */
    public boolean hasActiveConnection(String username) {
        return emitters.containsKey(username);
    }

    /**
     * Retorna el conteo de clientes SSE actualmente conectados.
     * Útil para monitoreo y debugging.
     *
     * @return El número de conexiones SSE activas
     */
    public int getActiveClientCount() {
        return emitters.size();
    }

    /**
     * Obtiene el emitter para un usuario específico.
     * Útil para enviar eventos SSE con nombre de evento específico.
     *
     * @param username El username del usuario
     * @return El SseEmitter o null si no existe
     */
    public SseEmitter getEmitter(String username) {
        return emitters.get(username);
    }

    /**
     * Elimina todos los clientes. Útil para shutdown o limpieza.
     */
    public void unregisterAll() {
        logger.info("[SseNotificationService] Desregistrando todos los clientes SSE. Conteo: {}", emitters.size());
        for (SseEmitter emitter : emitters.values()) {
            try {
                emitter.complete();
            } catch (Exception e) {
                logger.debug("[SseNotificationService] Error al completar emitter", e);
            }
        }
        emitters.clear();
        logger.info("[SseNotificationService] Todos los clientes SSE desregistrados");
    }
}
