package com.basic_chat.chat_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.basic_chat.chat_service.models.PendingDeletion;

public interface PendingDeletionRepository extends JpaRepository<PendingDeletion, Long>{
    List<PendingDeletion> findByRecipient(String recipient);
}
