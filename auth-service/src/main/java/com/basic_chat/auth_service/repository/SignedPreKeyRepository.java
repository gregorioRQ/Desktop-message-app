package com.basic_chat.auth_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.basic_chat.auth_service.model.SignedPreKey;

@Repository
public interface SignedPreKeyRepository extends JpaRepository<SignedPreKey, Long> {

    SignedPreKey findByRegistrationId(int registrationId);

}
