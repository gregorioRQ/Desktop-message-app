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
     * 1. Almacena el emitter en el mapa
     * 2. Marca al usuario como ONLINE en la base de datos
     * 3. Envía la lista de contactos online al cliente
     * 4. Notifica a los contactos del usuario que está ONLINE
     * 
     * @param userId El ID único del usuario que se suscribe
     * @param emitter El SseEmitter a usar para este cliente
     */
    public void registerClient(String userId, SseEmitter emitter) {
        logger.info("[SseNotificationService] Registrando cliente SSE para usuario: {}", userId);
        
        // Almacenar el emitter
        emitters.put(userId, emitter);
        logger.debug("[SseNotificationService] Cliente SSE registrado. Total clientes: {}", emitters.size());
        
        // Marcar usuario como ONLINE
        markUserOnline(userId);
        
        // Enviar lista de contactos online al cliente
        sendContactsList(userId);
        
        // Notificar a los contactos que este usuario está ONLINE
        notifyContactsOfPresence(userId, "ONLINE");
        
        logger.info("[SseNotificationService] Registro completado para usuario: {}. Contactos notificados.", userId);
    }

    /**
     * Marca a un usuario como ONLINE en la base de datos.
     * 
     * Este método actualiza el estado del usuario en la tabla users,
     * permitiendo que otros usuarios vean que está conectado.
     * 
     * @param userId ID del usuario a marcar como online
     */
    private void markUserOnline(String userId) {
        try {
            userRepository.findById(userId).ifPresent(user -> {
                user.setOnline(true);
                userRepository.save(user);
                logger.info("[SseNotificationService] Usuario {} marcado como ONLINE en BD", userId);
            });
        } catch (Exception e) {
            logger.error("[SseNotificationService] Error al marcar usuario {} como ONLINE: {}", userId, e.getMessage());
        }
    }

    /**
     * Marca a un usuario como OFFLINE en la base de datos.
     * 
     * Este método actualiza el estado del usuario cuando se detecta
     * que su conexión SSE se ha cerrado.
     * 
     * @param userId ID del usuario a marcar como offline
     */
    private void markUserOffline(String userId) {
        try {
            userRepository.findById(userId).ifPresent(user -> {
                user.setOnline(false);
                userRepository.save(user);
                logger.info("[SseNotificationService] Usuario {} marcado como OFFLINE en BD", userId);
            });
        } catch (Exception e) {
            logger.error("[SseNotificationService] Error al marcar usuario {} como OFFLINE: {}", userId, e.getMessage());
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
     * @param userId ID del usuario al que enviar la lista
     */
    public void sendContactsList(String userId) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            logger.warn("[SseNotificationService] No se puede enviar lista de contactos - cliente no conectado: {}", userId);
            return;
        }

        try {
            // Obtener contactos online del usuario
            List<String> onlineContactUsernames = contactUserRepository.findOnlineContactUsernamesByUserId(userId);
            logger.info("[SseNotificationService] Enviando lista de {} contactos online a usuario: {}", 
                    onlineContactUsernames.size(), userId);

            // Construir JSON con la lista de contactos
            ObjectNode rootNode = objectMapper.createObjectNode();
            ArrayNode contactsArray = rootNode.putArray("contacts");
            
            for (String username : onlineContactUsernames) {
                // Buscar el userId del contacto
                userRepository.findByUsername(username).ifPresent(contactUser -> {
                    ObjectNode contactNode = contactsArray.addObject();
                    contactNode.put("userId", contactUser.getId());
                    contactNode.put("username", username);
                });
            }

            String jsonPayload = objectMapper.writeValueAsString(rootNode);
            
            // Enviar evento SSE con nombre "contact_list"
            emitter.send(SseEmitter.event()
                    .name("contact_list")
                    .data(jsonPayload));
            
            logger.debug("[SseNotificationService] Lista de contactos enviada exitosamente a usuario: {}", userId);
            
        } catch (Exception e) {
            logger.error("[SseNotificationService] Error al enviar lista de contactos a usuario {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Notifica a los contactos de un usuario sobre su cambio de presencia.
     * 
     * Este método encuentra todos los contactos del usuario y les envía
     * un evento SSE con nombre "presence" indicando si el usuario está
     * ONLINE u OFFLINE.
     * 
     * @param userId ID del usuario que cambió de estado
     * @param type "ONLINE" o "OFFLINE"
     */
    private void notifyContactsOfPresence(String userId, String type) {
        try {
            // Obtener información del usuario
            var userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                logger.warn("[SseNotificationService] Usuario {} no encontrado para notificar presencia", userId);
                return;
            }
            
            var user = userOpt.get();
            String username = user.getUsername();
            
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
                String contactUserId = contact.getUserId();
                SseEmitter contactEmitter = emitters.get(contactUserId);
                
                if (contactEmitter != null) {
                    try {
                        contactEmitter.send(SseEmitter.event()
                                .name("presence")
                                .data(jsonPayload));
                        logger.debug("[SseNotificationService] Notificación {} enviada a contacto: {}", type, contactUserId);
                    } catch (IOException e) {
                        logger.warn("[SseNotificationService] Error al notificar a contacto {}: {}", contactUserId, e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            logger.error("[SseNotificationService] Error al notificar presencia de usuario {}: {}", userId, e.getMessage());
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
     * @param userId El ID del usuario a desregistrar
     */
    public void unregisterClient(String userId) {
        SseEmitter removed = emitters.remove(userId);
        if (removed != null) {
            try {
                removed.complete();
            } catch (Exception e) {
                logger.debug("[SseNotificationService] Error al completar emitter para usuario: {}", userId, e);
            }
            
            // Marcar usuario como OFFLINE
            markUserOffline(userId);
            
            // Notificar a los contactos que el usuario está OFFLINE
            notifyContactsOfPresence(userId, "OFFLINE");
            
            logger.info("[SseNotificationService] Cliente SSE desregistrado para usuario: {}. Clientes restantes: {}", 
                    userId, emitters.size());
        } else {
            logger.warn("[SseNotificationService] Intentó desregistrar cliente inexistente para usuario: {}", userId);
        }
    }

    /**
     * Envía una notificación simple a un usuario específico vía SSE.
     * 
     * Este método empuja un mensaje al stream SSE del usuario.
     * El mensaje se enviará como un evento SSE con el nombre por defecto.
     * 
     * @param userId El ID del usuario destinatario
     * @param message El mensaje de notificación a enviar
     * @return true si el mensaje fue enviado exitosamente, false en caso contrario
     */
    public boolean sendNotification(String userId, String message) {
        SseEmitter emitter = emitters.get(userId);

        if (emitter == null) {
            logger.warn("[SseNotificationService] No se puede enviar notificación - cliente no encontrado: {}", userId);
            return false;
        }

        try {
            emitter.send(message);
            logger.debug("[SseNotificationService] Notificación enviada exitosamente a usuario: {}", userId);
            return true;
        } catch (IOException e) {
            logger.error("[SseNotificationService] Error al enviar notificación a usuario {}: {}", userId, e.getMessage());
            unregisterClient(userId);
            return false;
        }
    }

    /**
     * Verifica si un usuario tiene una conexión SSE activa.
     * 
     * @param userId El ID del usuario a verificar
     * @return true si el usuario tiene una conexión SSE activa, false en caso contrario
     */
    public boolean hasActiveConnection(String userId) {
        return emitters.containsKey(userId);
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
     * @param userId El ID del usuario
     * @return El SseEmitter o null si no existe
     */
    public SseEmitter getEmitter(String userId) {
        return emitters.get(userId);
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
