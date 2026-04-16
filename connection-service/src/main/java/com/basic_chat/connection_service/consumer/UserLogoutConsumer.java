package com.basic_chat.connection_service.consumer;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.basic_chat.connection_service.config.UserLogoutRabbitConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserLogoutConsumer {

    private final StringRedisTemplate redisTemplate;
    private static final String USER_NAME_PREFIX = "user:name:";

    /**
     * Escucha eventos de logout de usuarios publicados por profile-service.
     * 
     * Cuando un usuario cierra sesión, profile-service publica un evento con el username
     * a la cola 'user.logout'. Este consumidor recibe el evento y elimina la clave
     * Redis user:name:{username} para limpiar el mapeo username -> userId.
     * 
     * @param username Nombre del usuario que ha cerrado sesión
     */
    @RabbitListener(queues = UserLogoutRabbitConfig.USER_LOGOUT_QUEUE)
    public void handleUserLogout(String username) {
        if (username == null || username.isEmpty()) {
            log.warn("Evento de logout recibido con username vacío");
            return;
        }

        String redisKey = USER_NAME_PREFIX + username;
        
        try {
            Boolean deleted = redisTemplate.delete(redisKey);
            
            if (Boolean.TRUE.equals(deleted)) {
                log.info("Clave Redis '{}' eliminada para usuario: {}", redisKey, username);
            } else {
                log.warn("Clave Redis '{}' no encontrada para usuario: {} (ya eliminada o nunca existió)", 
                    redisKey, username);
            }
        } catch (Exception e) {
            log.error("Error al eliminar clave Redis '{}' para usuario {}: {}", 
                redisKey, username, e.getMessage());
        }
    }
}
