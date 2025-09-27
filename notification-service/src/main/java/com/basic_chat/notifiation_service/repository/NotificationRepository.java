package com.basic_chat.notifiation_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.basic_chat.notifiation_service.model.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    public void deleteAllByReceiver(String receiver);

    public List<Notification> findByReceiverAndIsSeenFalse(String receiver);
}
