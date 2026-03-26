package com.basic_chat.notifiation_service.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuración de WebSocket STOMP (LEGACY - COMENTADO).
 * 
 * Este archivo contenía la configuración del servidor WebSocket para notificaciones
 * usando el protocolo STOMP. Fue reemplazado por SSE (Server-Sent Events).
 * 
 * Mantenido por si se necesita en el futuro para funcionalidades adicionales.
 * 
 * @deprecated Esta configuración ya no se utiliza. Las notificaciones se envían
 *             exclusivamente vía SSE a través de SseNotificationController.
 */
// @Configuration
// @EnableWebSocketMessageBroker
public class WebSocketConfig /*implements WebSocketMessageBrokerConfigurer*/ {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketConfig.class);

    // @Override
    // public void registerStompEndpoints(StompEndpointRegistry registry) {
    //     registry.addEndpoint("/ws-notifications")
    //             .setAllowedOriginPatterns("*");
    // }

    /**
     * Configura el broker de mensajes para el sistema de mensajes STOMP.
     * 
     * @deprecated Reemplazado por SSE
     */
    // @Override
    // public void configureMessageBroker(MessageBrokerRegistry registry) {
    //     registry.enableSimpleBroker("/topic", "/queue")
    //             .setHeartbeatValue(new long[]{25000, 25000})
    //             .setTaskScheduler(taskScheduler());
    //     registry.setApplicationDestinationPrefixes("/app");
    // }

    /**
     * Configura el canal de entrada del cliente WebSocket para interceptar mensajes
     * antes de ser procesados. Aquí se extrae el userId y username del frame STOMP CONNECT
     * y se establece como Principal para que esté disponible en los event listeners.
     * 
     * @deprecated Reemplazado por SSE
     */
    // @Override
    // public void configureClientInboundChannel(ChannelRegistration registration) {
    //     registration.interceptors(new ChannelInterceptor() {
    //         @Override
    //         public Message<?> preSend(Message<?> message, MessageChannel channel) {
    //             StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    //             if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
    //                 String userId = accessor.getFirstNativeHeader("userId");
    //                 String username = accessor.getFirstNativeHeader("username");
    //                 if (userId != null && !userId.isEmpty()) {
    //                     accessor.getSessionAttributes().put("userId", userId);
    //                     if (username != null && !username.isEmpty()) {
    //                         accessor.getSessionAttributes().put("username", username);
    //                         logger.info("Usuario autenticado en WebSocket: {} ({})", username, userId);
    //                     } else {
    //                         logger.info("Usuario autenticado en WebSocket: {}", userId);
    //                     }
    //                     accessor.setUser(new Principal() {
    //                         @Override
    //                         public String getName() {
    //                             return userId;
    //                         }
    //                     });
    //                 } else {
    //                     logger.warn("Conexión WebSocket sin header userId");
    //                 }
    //             }
    //             return message;
    //         }
    //     });
    // }

    /**
     * Crea el TaskScheduler para manejar heartbeats STOMP.
     * 
     * @deprecated Reemplazado por heartbeats SSE
     * @return TaskScheduler configurado para heartbeats
     */
    // @Bean
    // public TaskScheduler taskScheduler() {
    //     ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    //     scheduler.setPoolSize(2);
    //     scheduler.setThreadNamePrefix("websocket-heartbeat-");
    //     scheduler.setWaitForTasksToCompleteOnShutdown(true);
    //     scheduler.setAwaitTerminationSeconds(5);
    //     scheduler.initialize();
    //     logger.info("TaskScheduler para heartbeats WebSocket inicializado");
    //     return scheduler;
    // }
}
