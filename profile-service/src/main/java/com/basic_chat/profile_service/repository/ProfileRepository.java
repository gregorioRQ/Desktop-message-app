package com.basic_chat.profile_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.basic_chat.profile_service.models.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
    Profile findByUserId(Long userId);
}
