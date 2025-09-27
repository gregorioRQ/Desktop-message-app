package com.basic_chat.auth_service.service;

import java.util.Date;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.basic_chat.auth_service.model.PreKeyRequest;
import com.basic_chat.auth_service.model.Prekey;
import com.basic_chat.auth_service.repository.PreKeyRepository;

import jakarta.transaction.Transactional;

@Service
public class PrekeyService {
    private final PreKeyRepository preKeyRepo;

    public PrekeyService(PreKeyRepository preKeyRepo) {
        this.preKeyRepo = preKeyRepo;
    }

    @Transactional
    public void savePreKey(PreKeyRequest request) {

        // Recorrer el mapa de prekeys
        /*
         * for (Map.Entry<String, Object> entry : request.getPreKeys().entrySet()) {
         * String preKeyId = entry.getKey();
         * String preKeyValue = entry.getValue().toString();
         * Prekey key = new Prekey();
         * key.setPrekey(preKeyValue);
         * key.setPrekeyId(Integer.parseInt(preKeyId));
         * key.setTimestamp(new Date().toString());
         * key.setUserId(Long.valueOf(request.getUserId()));
         * preKeyRepo.save(key);
         * 
         * }
         */
    }

}
