package com.basic_chat.notifiation_service.controller;

import java.util.List;
import java.util.Map;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.basic_chat.notifiation_service.model.MessageSeenEvent;
import com.basic_chat.notifiation_service.model.Notification;
import com.basic_chat.notifiation_service.service.NotificationService;

@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationService notificationService;
    private final RabbitTemplate rabbitTemplate;

    public NotificationController(NotificationService notificationService, RabbitTemplate rabbitTemplate) {
        this.notificationService = notificationService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @GetMapping("/unseen")
    public ResponseEntity<Map<String, Object>> listarNotificacoesNoVistas(@RequestParam String username) {
        List<Notification> notifications = notificationService.getUnseenNotificationsByUsername(username);

        if (notifications.isEmpty()) {
            return ResponseEntity.ok(Map.of("message", "No new notifications"));
        } else {
            return ResponseEntity.ok(Map.of("notifications", notifications));
        }
    }

    @GetMapping("/seen")
    public ResponseEntity<String> marcarComoVisto(@RequestParam Long notificationId) {
        notificationService.deleteNotification(notificationId);
        return ResponseEntity.ok("Notificaión marcada como vista");
    }

    @GetMapping("/seen-all")
    public ResponseEntity<String> marcarTodasComoVisto(@RequestParam String username) {
        notificationService.deleteAllNotificationsByUsername(username);
        return ResponseEntity.ok("Todas as notificacióes marcadas como vistas");
    }

}
