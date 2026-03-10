package com.basic_chat.chat_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.basic_chat.chat_service.models.PendingBlock;

public interface PendingBlockRepository extends JpaRepository<PendingBlock, Long> {
    List<PendingBlock> findByBlockedUser(String blockedUser);
}
