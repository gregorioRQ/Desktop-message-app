package com.basic_chat.chat_service.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.basic_chat.chat_service.models.PendingUnblock;

public interface PendingUnblockRepository extends JpaRepository<PendingUnblock, Long> {
    List<PendingUnblock> findByUnblockedUser(String unblockedUser);
}