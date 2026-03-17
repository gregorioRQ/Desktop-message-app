package com.basic_chat.chat_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.basic_chat.chat_service.models.PendingBlock;

public interface PendingBlockRepository extends JpaRepository<PendingBlock, Long> {
    List<PendingBlock> findByBlockedUser(String blockedUser);

    /**
     * Busca bloqueos pendientes por el usuario que bloquea y el usuario bloqueado.
     * Usado para verificar si existe una solicitud de bloqueo pendiente.
     * 
     * @param blocker Usuario que quiere bloquear
     * @param blockedUser Usuario que sería bloqueado
     * @return Lista de bloqueos pendientes que coinciden
     */
    List<PendingBlock> findByBlockerAndBlockedUser(String blocker, String blockedUser);
}
