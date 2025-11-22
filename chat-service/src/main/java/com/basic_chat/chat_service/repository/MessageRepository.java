package com.basic_chat.chat_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.basic_chat.chat_service.models.Message;

import jakarta.transaction.Transactional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    //List<Message> findByReceiverAndIsSeenFalse(String to);

    //public boolean existsByReceiver(String receiver);

    //public void deleteAllMessagesByReceiver(String receiver);

    // Elimina todos los mensajes entre el remitente y el receptor especificados
    //public void deleteAllBySenderAndReceiver(String sender, String receiver);

    /* 
    @Modifying
    @Transactional
    @Query("UPDATE Message m SET m.isSeen = true WHERE m.receiver = :receiver AND m.id IN :ids")
    int markMessagesAsRead(@Param("receiver") String receiver, @Param("ids") List<Long> ids);*/
}
