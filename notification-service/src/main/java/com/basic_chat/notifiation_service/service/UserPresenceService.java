package com.basic_chat.notifiation_service.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.notifiation_service.model.User;
import com.basic_chat.notifiation_service.model.UserContact;
import com.basic_chat.notifiation_service.repository.UserContactRepository;
import com.basic_chat.notifiation_service.repository.UserRepository;

/**
 * Servicio responsable de gestionar la presencia de usuarios y las suscripciones de sesiones.
 * Maneja la lógica de conexión/desconexión de sesiones WebSocket y notificaciones de presencia.
 */
@Service
@Transactional
public class UserPresenceService {
    private static final Logger logger = LoggerFactory.getLogger(UserPresenceService.class);
    
    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final UserContactRepository userContactRepository;

    public UserPresenceService(StringRedisTemplate redisTemplate, SimpMessagingTemplate messagingTemplate,
                               UserRepository userRepository, UserContactRepository userContactRepository) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
        this.userContactRepository = userContactRepository;
    }

    /**
     * Registra una nueva sesión de usuario en Redis y configura las suscripciones a sus contactos.
     * Este método es invocado cuando un cliente establece una conexión WebSocket.
     * 
     * Proceso:
     * 1. Verifica si la sesión ya existe en Redis (evita duplicados)
     * 2. Registra la nueva sesión en Redis
     * 3. Obtiene los contactos del usuario desde la base de datos
     * 4. Suscribe la sesión a los cambios de presencia de todos los contactos
     * 
     * @param userId ID del usuario que se conecta
     * @param sessionId ID único de la sesión WebSocket
     * @throws IllegalArgumentException si userId o sessionId están vacíos
     * @throws RuntimeException si ocurre un error inesperado
     */
    public void handleSessionConnected(String userId, String sessionId) {
        try {
            // Validar que los parámetros requeridos no sean nulos
            if (userId == null || userId.trim().isEmpty() || sessionId == null || sessionId.trim().isEmpty()) {
                logger.warn("Parámetros inválidos en handleSessionConnected. userId: {}, sessionId: {}", userId, sessionId);
                return;
            }
            
            logger.info("Nueva conexión WebSocket. Usuario: {}, Sesión: {}", userId, sessionId);
            
            // Verificar si la sesión ya existe en Redis (evita registros duplicados)
            logger.debug("Verificando si el usuario {} ya tiene la sesión {} registrada", userId, sessionId);
            List<String> existingSessions = redisTemplate.opsForList()
                    .range("user:" + userId + ":sessions", 0, -1);
            
            if (existingSessions != null && existingSessions.contains(sessionId)) {
                logger.info("Sesión {} ya registrada para el usuario {}. No se creará duplicado", sessionId, userId);
                return;
            }
            
            // Registrar la nueva sesión en Redis
            logger.debug("Registrando nueva sesión {} para el usuario {}", sessionId, userId);
            redisTemplate.opsForList().rightPush("user:" + userId + ":sessions", sessionId);
            redisTemplate.opsForValue().set("session:" + sessionId + ":user", userId);
            logger.debug("Sesión {} registrada correctamente en Redis", sessionId);

            // Obtener el usuario desde la base de datos para acceder a sus contactos
            Optional<User> userOpt = userRepository.findById(userId);
            
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                logger.debug("Usuario {} encontrado en la base de datos", userId);
                
                // Obtener lista de contactos del usuario
                List<UserContact> contacts = userContactRepository.findByUser(user);
                
                if (contacts.isEmpty()) {
                    logger.info("Usuario {} no tiene contactos registrados", userId);
                } else {
                    logger.info("Usuario {} tiene {} contactos. Suscribiendo sesión {} a todos ellos", 
                        userId, contacts.size(), sessionId);
                    
                    // Suscribir la sesión a los cambios de presencia de cada contacto
                    for (UserContact contact : contacts) {
                        String contactId = contact.getContact().getId();
                        try {
                            subscribeToContact(sessionId, contactId);
                            logger.debug("Sesión {} suscrita al contacto {}", sessionId, contactId);
                        } catch (Exception e) {
                            logger.error("Error al suscribir sesión {} al contacto {}. Error: {}", 
                                sessionId, contactId, e.getMessage(), e);
                        }
                    }
                    logger.info("Suscripciones completadas para la sesión {}", sessionId);
                }
            } else {
                logger.warn("Usuario {} no encontrado en la base de datos. Sesión registrada pero sin suscripciones a contactos", userId);
            }
            
            logger.info("Proceso de conexión completado exitosamente para usuario: {}, sesión: {}", userId, sessionId);
            
        } catch (Exception e) {
            logger.error("Error inesperado al procesar conexión para usuario: {}, sesión: {}. Error: {}", 
                userId, sessionId, e.getMessage(), e);
            throw new RuntimeException(
                "Error al procesar la conexión de la sesión " + sessionId + " para el usuario " + userId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Maneja la desconexión de una sesión WebSocket.
     * 
     * Proceso:
     * 1. Obtiene el userId asociado a la sesión desde Redis
     * 2. Elimina la sesión de la lista de sesiones activas del usuario
     * 3. Si el usuario no tiene más sesiones activas, notifica su desconexión
     * 4. Limpia las suscripciones de la sesión
     * 5. Elimina los datos de la sesión en Redis
     * 
     * @param sessionId ID único de la sesión WebSocket a desconectar
     */
    public void handleSessionDisconnect(String sessionId) {
        try {
            if (sessionId == null || sessionId.trim().isEmpty()) {
                logger.warn("sessionId inválido en handleSessionDisconnect: {}", sessionId);
                return;
            }
            
            logger.info("Iniciando proceso de desconexión para sesión: {}", sessionId);
            
            // Obtener el userId asociado a esta sesión desde Redis
            String userId = redisTemplate.opsForValue().get("session:" + sessionId + ":user");
            
            if (userId != null) {
                logger.debug("Removiendo sesión {} de la lista activa del usuario: {}", sessionId, userId);
                redisTemplate.opsForList().remove("user:" + userId + ":sessions", 0, sessionId);
                logger.info("Sesión {} eliminada de la lista activa del usuario: {}", sessionId, userId);

                // Verificar si al usuario le quedan sesiones activas; si no, notificar desconexión
                List<String> remainingSessions = redisTemplate.opsForList().range("user:" + userId + ":sessions", 0, -1);
                if (remainingSessions == null || remainingSessions.isEmpty()) {
                    logger.debug("Usuario {} no tiene más sesiones activas. Notificando desconexión", userId);
                    notifyUserOffline(userId);
                }
            } else {
                logger.warn("No se encontró userId asociado a la sesión: {}", sessionId);
            }

            // Obtener y eliminar todas las suscripciones de esta sesión
            List<String> subscriptions = redisTemplate.opsForList().range("session:" + sessionId + ":subscriptions", 0, -1);
            if (subscriptions != null && !subscriptions.isEmpty()) {
                logger.debug("Removiendo suscripciones para la sesión {}. Total: {}", sessionId, subscriptions.size());
                for (String contactId : subscriptions) {
                    redisTemplate.opsForList().remove("user:" + contactId + ":subscribers", 0, sessionId);
                }
                logger.info("Suscripciones eliminadas. La sesión dejó de escuchar a {} contactos", subscriptions.size());
            }
            
            // Limpiar datos de la sesión en Redis
            redisTemplate.delete("session:" + sessionId + ":subscriptions");
            redisTemplate.delete("session:" + sessionId + ":user");
            logger.info("Limpieza de Redis completada para la sesión: {}", sessionId);
            
        } catch (Exception e) {
            logger.error("Error inesperado al procesar desconexión para sesión: {}. Error: {}", 
                sessionId, e.getMessage(), e);
            throw new RuntimeException(
                "Error al procesar la desconexión de la sesión " + sessionId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Notifica a todos los suscriptores de un usuario que este ha conectado.
     * 
     * Envía un mensaje de presencia online a través de WebSocket a cada usuario que está
     * suscrito a los cambios de presencia del usuario especificado.
     * 
     * @param userId ID del usuario que se conectó
     */
    public void notifyUserOnline(String userId) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                logger.warn("userId inválido en notifyUserOnline: {}", userId);
                return;
            }
            
            logger.info("Notificando a suscriptores que el usuario {} está conectado", userId);
            
            // Obtener todos los suscriptores de este usuario
            List<String> subscribers = redisTemplate.opsForList().range("user:" + userId + ":subscribers", 0, -1);
            
            if (subscribers != null && !subscribers.isEmpty()) {
                logger.debug("Usuario {} tiene {} suscriptores. Enviando notificación de presencia", 
                    userId, subscribers.size());
                
                Map<String, String> payload = Map.of("type", "user_online", "userId", userId);
                Map<String, Object> headers = Map.of("type", "user_online");
                java.util.Set<String> notifiedUsers = new java.util.HashSet<>();
                
                for (String sessionId : subscribers) {
                    // Recuperar el userId asociado a cada sesión desde Redis
                    String subscriberId = redisTemplate.opsForValue().get("session:" + sessionId + ":user");
                    
                    if (subscriberId != null && !notifiedUsers.contains(subscriberId)) {
                        try {
                            // Enviar al destino específico: /queue/presence/{subscriberId}
                            logger.debug("Enviando notificación de presencia online a: {}", subscriberId);
                            messagingTemplate.convertAndSend("/queue/presence/" + subscriberId, payload, headers);
                            notifiedUsers.add(subscriberId);
                        } catch (Exception e) {
                            logger.error("Error al enviar notificación de presencia a {}. Error: {}", 
                                subscriberId, e.getMessage(), e);
                        }
                    }
                }
                logger.info("Notificaciones de presencia online enviadas a {} usuarios", notifiedUsers.size());
            } else {
                logger.debug("Usuario {} no tiene suscriptores activos", userId);
            }

        } catch (Exception e) {
            logger.error("Error inesperado al notificar presencia online del usuario: {}. Error: {}", 
                userId, e.getMessage(), e);
            throw new RuntimeException(
                "Error al notificar presencia online del usuario " + userId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Notifica a todos los suscriptores de un usuario que este se ha desconectado.
     * 
     * Envía un mensaje de presencia offline a través de WebSocket a cada usuario que está
     * suscrito a los cambios de presencia del usuario especificado.
     * 
     * @param userId ID del usuario que se desconectó
     */
    public void notifyUserOffline(String userId) {
        try {
            if (userId == null || userId.trim().isEmpty()) {
                logger.warn("userId inválido en notifyUserOffline: {}", userId);
                return;
            }
            
            logger.info("Notificando a suscriptores que el usuario {} se ha desconectado", userId);
            
            // Obtener todos los suscriptores de este usuario
            List<String> subscribers = redisTemplate.opsForList().range("user:" + userId + ":subscribers", 0, -1);
            
            if (subscribers != null && !subscribers.isEmpty()) {
                logger.debug("Usuario {} tiene {} suscriptores. Enviando notificación de desconexión", 
                    userId, subscribers.size());
                
                Map<String, String> payload = Map.of("type", "user_offline", "userId", userId);
                Map<String, Object> headers = Map.of("type", "user_offline");
                java.util.Set<String> notifiedUsers = new java.util.HashSet<>();
                
                for (String sessionId : subscribers) {
                    String subscriberId = redisTemplate.opsForValue().get("session:" + sessionId + ":user");
                    
                    if (subscriberId != null && !notifiedUsers.contains(subscriberId)) {
                        try {
                            logger.debug("Enviando notificación de presencia offline a: {}", subscriberId);
                            messagingTemplate.convertAndSend("/queue/presence/" + subscriberId, payload, headers);
                            notifiedUsers.add(subscriberId);
                        } catch (Exception e) {
                            logger.error("Error al enviar notificación de presencia offline a {}. Error: {}", 
                                subscriberId, e.getMessage(), e);
                        }
                    }
                }
                logger.info("Notificaciones de presencia offline enviadas a {} usuarios", notifiedUsers.size());
            } else {
                logger.debug("Usuario {} no tiene suscriptores activos", userId);
            }
   
        } catch (Exception e) {
            logger.error("Error inesperado al notificar presencia offline del usuario: {}. Error: {}", 
                userId, e.getMessage(), e);
            throw new RuntimeException(
                "Error al notificar presencia offline del usuario " + userId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Suscribe una sesión a los cambios de presencia de un contacto.
     * 
     * Registra en Redis:
     * - La sesión como suscriptora del contacto
     * - El contacto como suscripción de la sesión
     * 
     * @param sessionId ID de la sesión que se suscribe
     * @param contactId ID del contacto al que se suscribe
     */
    public void subscribeToContact(String sessionId, String contactId) {
        try {
            if (sessionId == null || sessionId.trim().isEmpty() || contactId == null || contactId.trim().isEmpty()) {
                logger.warn("Parámetros inválidos en subscribeToContact. sessionId: {}, contactId: {}", sessionId, contactId);
                return;
            }
            
            logger.debug("Suscribiendo sesión {} al contacto {}", sessionId, contactId);
            redisTemplate.opsForList().rightPush("user:" + contactId + ":subscribers", sessionId);
            redisTemplate.opsForList().rightPush("session:" + sessionId + ":subscriptions", contactId);
            logger.debug("Suscripción registrada: sesión {} -> contacto {}", sessionId, contactId);
            
        } catch (Exception e) {
            logger.error("Error inesperado al suscribir sesión {} al contacto {}. Error: {}", 
                sessionId, contactId, e.getMessage(), e);
            throw new RuntimeException(
                "Error al suscribir sesión " + sessionId + " al contacto " + contactId + ": " + e.getMessage(), e);
        }
    }
}
