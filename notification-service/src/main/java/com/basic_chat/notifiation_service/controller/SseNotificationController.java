package com.basic_chat.notifiation_service.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.basic_chat.notifiation_service.service.SseNotificationService;

@RestController
@RequestMapping("/api/notifications")
public class SseNotificationController {

    private static final Logger logger = LoggerFactory.getLogger(SseNotificationController.class);

    private static final String DEFAULT_EVENT_NAME = "message";
    private static final long HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final long STREAM_TIMEOUT_HOURS = 1;

    private final SseNotificationService sseNotificationService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public SseNotificationController(SseNotificationService sseNotificationService) {
        this.sseNotificationService = sseNotificationService;
    }

    /**
    * Establishes an SSE (Server-Sent Events) connection for receiving notifications.
    *
    * This endpoint allows clients to subscribe to real-time notifications via a
    * long-lived HTTP connection. The connection remains open until the client
    * disconnects, allowing the server to push notifications instantly.
    *
    * The stream includes:
    * - Heartbeat events every 30 seconds to keep the connection alive
    * - Notification events when new messages arrive
    *
    * @param username The username of the user subscribing to notifications
    * @return SseEmitter that provides the SSE stream to the client
    */
    @GetMapping(value = "/subscribe/{username}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> subscribe(@PathVariable String username) {
        logger.info("SSE subscription request received for user: {}", username);

        SseEmitter emitter = new SseEmitter(TimeUnit.HOURS.toMillis(STREAM_TIMEOUT_HOURS));

        final SseEmitter.SseEventBuilder heartbeatBuilder = SseEmitter.event()
            .id(String.valueOf(System.currentTimeMillis()))
            .name("heartbeat")
            .data(":ok\n\n");

        final Runnable heartbeatTask = () -> {
            try {
                emitter.send(heartbeatBuilder);
                logger.debug("Heartbeat sent to user: {}", username);
            } catch (IOException e) {
                logger.warn("Failed to send heartbeat to user: {}. Error: {}", username, e.getMessage());
            }
        };

        scheduler.scheduleAtFixedRate(heartbeatTask, HEARTBEAT_INTERVAL_SECONDS,
            HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        sseNotificationService.registerClient(username, emitter);

        emitter.onCompletion(() -> {
            logger.info("SSE stream completed for user: {}", username);
            sseNotificationService.unregisterClient(username);
        });

        emitter.onError(e -> {
            logger.error("SSE stream error for user: {}. Error: {}", username, e.getMessage());
            sseNotificationService.unregisterClient(username);
        });

        emitter.onTimeout(() -> {
            logger.warn("SSE stream timeout for user: {}", username);
            sseNotificationService.unregisterClient(username);
        });

        logger.info("SSE stream established for user: {}", username);
        return ResponseEntity.ok(emitter);
    }

    /**
     * Sends a notification event to a specific user via SSE.
     *
     * This endpoint is called by internal services (like NotificationConsumer)
     * to push notifications to connected clients.
     *
     * @param username The username of the recipient user
     * @param message The notification message to send
     * @return ResponseEntity with success status
     */
    @GetMapping("/send/{username}/{message}")
    public ResponseEntity<String> sendNotification(@PathVariable String username, @PathVariable String message) {
        logger.info("Sending notification to user: {}", username);

        boolean success = sseNotificationService.sendNotification(username, message);

        if (success) {
            logger.debug("Notification sent successfully to user: {}", username);
            return ResponseEntity.ok("{\"status\": \"success\"}");
        } else {
            logger.warn("Failed to send notification to user: {}. No active connection", username);
            return ResponseEntity.status(404).body("{\"status\": \"error\", \"message\": \"No active connection\"}");
        }
    }

    /**
     * Checks if a user has an active SSE connection.
     *
     * @param username The username to check
     * @return ResponseEntity with status information
     */
    @GetMapping("/status/{username}")
    public ResponseEntity<String> getConnectionStatus(@PathVariable String username) {
        boolean isConnected = sseNotificationService.hasActiveConnection(username);
        int activeClients = sseNotificationService.getActiveClientCount();

        String status = String.format(
            "{\"username\": \"%s\", \"connected\": %b, \"activeClients\": %d, \"timestamp\": \"%s\"}",
            username, isConnected, activeClients, Instant.now()
        );

        logger.debug("Connection status request for user: {}. Connected: {}", username, isConnected);
        return ResponseEntity.ok(status);
    }
}
