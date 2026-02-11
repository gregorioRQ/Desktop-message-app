package com.pola.media_service.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.pola.media_service.model.MediaEntity;

@Repository
public interface MediaRepository extends JpaRepository<MediaEntity, Long>{
    Optional<MediaEntity> findByMediaId(String mediaId);
    
    List<MediaEntity> findByReceiverIdAndDeliveredFalse(String receiverId);
    
    @Query("SELECT m FROM MediaEntity m WHERE m.deliveredAt < :date AND m.delivered = true")
    List<MediaEntity> findDeliveredMediaOlderThan(@Param("date") LocalDateTime date);
    
    void deleteByMediaId(String mediaId);
    
    //@Query("SELECT m FROM MediaEntity m WHERE m.senderId = :userId OR m.receiverId = :userId")
    //List<MediaEntity> findAllByUser(@Param("userId") Long userId);
}
