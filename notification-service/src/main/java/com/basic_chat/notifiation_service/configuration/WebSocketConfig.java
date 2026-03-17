package com.basic_chat.notifiation_service.configuration;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-notifications")
                .setAllowedOriginPatterns("*");
    }

    /**
     * Configura el broker de mensajes para el sistema de mensajes STOMP.
     * 
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{25000, 25000})
                .setTaskScheduler(taskScheduler());
        registry.setApplicationDestinationPrefixes("/app");
    }

    /**
     * Configura el canal de entrada del cliente WebSocket para interceptar mensajes
     * antes de ser procesados. Aquí se extrae el userId y username del frame STOMP CONNECT
     * y se establece como Principal para que esté disponible en los event listeners.
     * 
     * Flujo:
     * 1. Intercepta el comando CONNECT de STOMP
     * 2. Lee el header "userId" del frame STOMP
     * 3. Lee el header "username" del frame STOMP
     * 4. Guarda ambos en los headers del mensaje para acceso posterior
     * 5. Crea un Principal con el userId para hacerlo accesible en toda la sesión
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Lee el header "userId" del frame STOMP CONNECT (no del handshake HTTP)
                    String userId = accessor.getFirstNativeHeader("userId");
                    // Lee el header "username" del frame STOMP CONNECT
                    String username = accessor.getFirstNativeHeader("username");
                    
                    if (userId != null && !userId.isEmpty()) {
                        // Guardar username en headers de la sesión para acceso posterior
                        accessor.getSessionAttributes().put("userId", userId);
                        if (username != null && !username.isEmpty()) {
                            accessor.getSessionAttributes().put("username", username);
                            logger.info("Usuario autenticado en WebSocket: {} ({})", username, userId);
                        } else {
                            logger.info("Usuario autenticado en WebSocket: {}", userId);
                        }
                        
                        // Establece el Principal para que esté disponible en los event listeners
                        // mediante headerAccessor.getUser()
                        accessor.setUser(new Principal() {
                            @Override
                            public String getName() {
                                return userId;
                            }
                        });
                    } else {
                        logger.warn("Conexión WebSocket sin header userId");
                    }
                }
                return message;
            }
        });
    }

    /**
     * Crea el TaskScheduler para manejar heartbeats STOMP.
     * 
     * El scheduler necesita suficientes threads para manejar múltiples conexiones
     * simultáneas. Usamos pool size de 2 (suficiente para la mayoría de casos).
     * 
     * @return TaskScheduler configurado para heartbeats
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("websocket-heartbeat-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        scheduler.initialize();
        logger.info("TaskScheduler para heartbeats WebSocket inicializado");
        return scheduler;
    }
}
