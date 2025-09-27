package com.basic_chat.contact_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.basic_chat.contact_service.model.ContactBlocked;

public interface ContactBlockedRepository extends JpaRepository<ContactBlocked, Long> {
    boolean existsByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    Optional<ContactBlocked> findByUserIdAndBlockedUserId(Long userId, Long blockedUserId);

    List<ContactBlocked> findAllByUserId(Long userId);
}
