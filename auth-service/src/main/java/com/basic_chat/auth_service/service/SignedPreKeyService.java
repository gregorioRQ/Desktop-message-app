package com.basic_chat.auth_service.service;

import java.util.Date;

import org.springframework.stereotype.Service;

import com.basic_chat.auth_service.model.SignedPreKey;
import com.basic_chat.auth_service.model.SignedPreKeyRequest;
import com.basic_chat.auth_service.repository.SignedPreKeyRepository;

import jakarta.transaction.Transactional;

@Service
public class SignedPreKeyService {
    private final SignedPreKeyRepository keyRepo;

    public SignedPreKeyService(SignedPreKeyRepository keyRepo) {
        this.keyRepo = keyRepo;
    }

    @Transactional
    public void saveSignedKey(SignedPreKeyRequest request) {

    }

}
