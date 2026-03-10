package com.basic_chat.chat_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.basic_chat.chat_service.models.PendingClearHistory;

@Repository
public interface PendingClearHistoryRepository extends JpaRepository<PendingClearHistory, Long> {
    
    List<PendingClearHistory> findByRecipient(String recipient);
}
