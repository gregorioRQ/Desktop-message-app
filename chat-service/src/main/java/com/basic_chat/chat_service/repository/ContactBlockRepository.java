package com.basic_chat.chat_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.basic_chat.chat_service.models.ContactBlock;

@Repository
public interface ContactBlockRepository extends JpaRepository<ContactBlock, Long> {
    boolean existsByBlockerAndBlocked(String blocker, String blocked);
    void deleteByBlockerAndBlocked(String blocker, String blocked);
}
