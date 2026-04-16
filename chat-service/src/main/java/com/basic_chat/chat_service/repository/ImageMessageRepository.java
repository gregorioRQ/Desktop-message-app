package com.basic_chat.chat_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.basic_chat.chat_service.models.ImageMessage;

import jakarta.transaction.Transactional;

/**
 * Repositorio para gestionar mensajes de imagen pendientes.
 * Se utiliza para guardar y recuperar imágenes cuando el destinatario está offline.
 */
@Repository
public interface ImageMessageRepository extends JpaRepository<ImageMessage, Long> {

    /**
     * Busca todos los mensajes de imagen pendientes para un receptor específico.
     * Solo retorna mensajes que no han sido entregados aún.
     *
     * @param receiverId ID del usuario receptor
     * @return Lista de mensajes de imagen pendientes
     */
    List<ImageMessage> findByReceiverIdAndDeliveredFalse(String receiverId);

    /**
     * Elimina todos los mensajes de imagen para un receptor específico.
     * Se utiliza después de entregar los mensajes pendientes al cliente.
     *
     * @param receiverId ID del usuario receptor
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM ImageMessage i WHERE i.receiverId = :receiverId")
    void deleteByReceiverId(@Param("receiverId") String receiverId);
}
