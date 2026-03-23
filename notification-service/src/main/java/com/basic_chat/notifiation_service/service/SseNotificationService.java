package com.basic_chat.notifiation_service.service;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class SseNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(SseNotificationService.class);

    private static final String DEFAULT_EVENT_NAME = "message";

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * Registers a new SSE client for a specific user.
     * 
     * This method stores the SseEmitter to allow pushing events to the connected
     * client. The client is identified by userId.
     * 
     * @param userId  The unique identifier of the user subscribing to notifications
     * @param emitter The SseEmitter to use for this client
     */
    public void registerClient(String userId, SseEmitter emitter) {
        logger.info("Registering SSE client for user: {}", userId);
        emitters.put(userId, emitter);
        logger.debug("SSE client registered successfully. Total clients: {}", emitters.size());
    }

    /**
     * Unregisters an SSE client, removing it from the active clients map.
     * 
     * This should be called when the client disconnects or when the
     * subscription is no longer needed.
     * 
     * @param userId The unique identifier of the user to unregister
     */
    public void unregisterClient(String userId) {
        SseEmitter removed = emitters.remove(userId);
        if (removed != null) {
            try {
                removed.complete();
            } catch (Exception e) {
                logger.debug("Error completing emitter for user: {}", userId, e);
            }
            logger.info("SSE client unregistered for user: {}. Remaining clients: {}", 
                    userId, emitters.size());
        } else {
            logger.warn("Attempted to unregister non-existent SSE client for user: {}", userId);
        }
    }

    /**
     * Sends a notification event to a specific user via SSE.
     * 
     * This method pushes a message to the user's SSE stream. The message
     * will be sent as an SSE event with the default event name "message".
     * 
     * @param userId   The unique identifier of the recipient user
     * @param message  The notification message to send
     * @return true if the message was sent successfully, false otherwise
     */
    public boolean sendNotification(String userId, String message) {
        SseEmitter emitter = emitters.get(userId);

        if (emitter == null) {
            logger.warn("Cannot send notification - no SSE client found for user: {}", userId);
            return false;
        }

        try {
            // Enviar solo el mensaje - Spring SseEmitter ya añade el prefijo "data: " automáticamente
            emitter.send(message);
            logger.debug("Notification sent successfully to user: {}", userId);
            return true;
        } catch (IOException e) {
            logger.error("Failed to send notification to user: {}. Error: {}", userId, e.getMessage());
            unregisterClient(userId);
            return false;
        }
    }

    /**
     * Checks if a user has an active SSE connection.
     * 
     * @param userId The unique identifier of the user to check
     * @return true if the user has an active SSE connection, false otherwise
     */
    public boolean hasActiveConnection(String userId) {
        return emitters.containsKey(userId);
    }

    /**
     * Returns the count of currently connected SSE clients.
     * Useful for monitoring and debugging purposes.
     * 
     * @return The number of active SSE connections
     */
    public int getActiveClientCount() {
        return emitters.size();
    }

    /**
     * Removes all clients. Useful for shutdown or cleanup operations.
     */
    public void unregisterAll() {
        logger.info("Unregistering all SSE clients. Count: {}", emitters.size());
        for (SseEmitter emitter : emitters.values()) {
            try {
                emitter.complete();
            } catch (Exception e) {
                logger.debug("Error completing emitter", e);
            }
        }
        emitters.clear();
        logger.info("All SSE clients unregistered");
    }
}
