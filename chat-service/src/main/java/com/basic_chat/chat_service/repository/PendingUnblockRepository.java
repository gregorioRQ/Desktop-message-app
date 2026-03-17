package com.basic_chat.chat_service.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.basic_chat.chat_service.models.PendingUnblock;

public interface PendingUnblockRepository extends JpaRepository<PendingUnblock, Long> {
    List<PendingUnblock> findByUnblockedUser(String unblockedUser);

    /**
     * Busca desbloqueos pendientes por el usuario que desbloquea y el usuario desbloqueado.
     * Usado para verificar si existe una solicitud de desbloqueo pendiente.
     * 
     * @param blocker Usuario que quiere desbloquear
     * @param unblockedUser Usuario que sería desbloqueado
     * @return Lista de desbloqueos pendientes que coinciden
     */
    List<PendingUnblock> findByBlockerAndUnblockedUser(String blocker, String unblockedUser);
}