package com.basic_chat.chat_service.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.basic_chat.chat_service.models.PendingContactIdentity;

public interface PendingContactIdentityRepository extends JpaRepository<PendingContactIdentity, Long> {
    List<PendingContactIdentity> findByRecipient(String recipient);
}

