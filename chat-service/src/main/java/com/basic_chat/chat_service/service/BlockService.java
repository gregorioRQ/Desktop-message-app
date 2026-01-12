package com.basic_chat.chat_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.chat_service.models.ContactBlock;
import com.basic_chat.chat_service.repository.ContactBlockRepository;



@Service
public class BlockService {
    private final ContactBlockRepository blockRepository;

    public BlockService(ContactBlockRepository blockRepository){
        this.blockRepository = blockRepository;
    }

    @Transactional
    public void blockUser(String blocker, String blocked) {
        if (blocker.equals(blocked)) {
            throw new IllegalArgumentException("No puedes bloquearte a ti mismo");
        }
        
        // Idempotencia: Si ya existe el bloqueo, consideramos la operación exitosa
        if (blockRepository.existsByBlockerAndBlocked(blocker, blocked)) {
            return;
        }
        
        blockRepository.save(new ContactBlock(blocker, blocked));
    }

    @Transactional
    public void unblockUser(String blocker, String blocked) {
        blockRepository.deleteByBlockerAndBlocked(blocker, blocked);
    }

    /**
     * Verifica si el envío de mensajes está permitido.
     * Retorna true si el 'recipient' ha bloqueado al 'sender'.
     */
    public boolean isBlocked(String sender, String recipient) {
        return blockRepository.existsByBlockerAndBlocked(recipient, sender);
    }

}
