package com.pola.media_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pola.media_service.exception.MediaNotFoundException;
import com.pola.media_service.model.MediaEntity;
import com.pola.media_service.repository.MediaRepository;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.pola.media_service.constants.MediaConstants.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de gestión de metadata en base de datos.
 * Responsable de todas las operaciones CRUD sobre MediaEntity.
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataService {

     private final MediaRepository mediaRepository;
    
    /**
     * Guarda metadata de un nuevo media en la base de datos.
     * 
     * @param entity Entidad a guardar
     * @return Entidad guardada con ID generado
     */
    @Transactional
    public MediaEntity saveMetadata(MediaEntity entity) {
        log.info("Saving media metadata: mediaId={}, senderId={}, receiverId={}",
            entity.getMediaId(), entity.getSenderId(), entity.getReceiverId());
        
        try {
            MediaEntity savedEntity = mediaRepository.save(entity);
            log.debug("Media metadata saved successfully: id={}, mediaId={}", 
                savedEntity.getId(), savedEntity.getMediaId());
            return savedEntity;
            
        } catch (Exception e) {
            log.error("Failed to save media metadata: mediaId={}", entity.getMediaId(), e);
            throw e;
        }
    }

    /**
     * Busca un media por su mediaId.
     * 
     * @param mediaId ID único del media
     * @return MediaEntity encontrada
     * @throws MediaNotFoundException si no existe
     */
    @Transactional(readOnly = true)
    public MediaEntity findByMediaId(String mediaId) {
        log.debug("Finding media by mediaId: {}", mediaId);
        
        return mediaRepository.findByMediaId(mediaId)
            .orElseThrow(() -> {
                log.error("Media not found: mediaId={}", mediaId);
                return new MediaNotFoundException(mediaId);
            });
    }

     /**
     * Busca un media por su mediaId, retornando Optional.
     * 
     * @param mediaId ID único del media
     * @return Optional con MediaEntity si existe
     */
    @Transactional(readOnly = true)
    public Optional<MediaEntity> findByMediaIdOptional(String mediaId) {
        log.debug("Finding media by mediaId (optional): {}", mediaId);
        return mediaRepository.findByMediaId(mediaId);
    }

    /**
     * Marca un media como entregado.
     * 
     * @param mediaId ID del media
     * @throws MediaNotFoundException si no existe
     */
    @Transactional
    public void markAsDelivered(String mediaId) {
        log.info("Marking media as delivered: mediaId={}", mediaId);
        
        MediaEntity media = findByMediaId(mediaId);
        media.setDelivered(true);
        media.setDeliveredAt(LocalDateTime.now());
        
        mediaRepository.save(media);
        log.debug("Media marked as delivered: mediaId={}", mediaId);
    }

        /**
     * Obtiene todos los medias pendientes de entrega para un receptor.
     * 
     * @param receiverId ID del receptor
     * @return Lista de medias pendientes
     */
    @Transactional(readOnly = true)
    public List<MediaEntity> findPendingMediaForReceiver(String receiverId) {
        log.debug("Finding pending media for receiver: receiverId={}", receiverId);
        
        List<MediaEntity> pendingMedia = mediaRepository.findByReceiverIdAndDeliveredFalse(receiverId);
        log.debug("Found {} pending media for receiver: receiverId={}", 
            pendingMedia.size(), receiverId);
        
        return pendingMedia;
    }

    /**
     * Obtiene medias entregados hace más de X días.
     * Útil para limpieza automática.
     * 
     * @param days Días desde la entrega
     * @return Lista de medias antiguos
     */
    @Transactional(readOnly = true)
    public List<MediaEntity> findDeliveredMediaOlderThan(int days) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        
        log.debug("Finding delivered media older than {} days (before {})", days, cutoffDate);
        
        List<MediaEntity> oldMedia = mediaRepository.findDeliveredMediaOlderThan(cutoffDate);
        log.debug("Found {} delivered media older than {} days", oldMedia.size(), days);
        
        return oldMedia;
    }

    /**
     * Elimina un media de la base de datos.
     * 
     * @param mediaId ID del media
     */
    @Transactional
    public void deleteByMediaId(String mediaId) {
        log.info("Deleting media metadata: mediaId={}", mediaId);
        
        try {
            mediaRepository.deleteByMediaId(mediaId);
            log.debug("Media metadata deleted: mediaId={}", mediaId);
            
        } catch (Exception e) {
            log.error("Failed to delete media metadata: mediaId={}", mediaId, e);
            throw e;
        }
    }

    /**
     * Elimina una entidad media.
     * 
     * @param entity Entidad a eliminar
     */
    @Transactional
    public void delete(MediaEntity entity) {
        log.info("Deleting media entity: id={}, mediaId={}", entity.getId(), entity.getMediaId());
        
        try {
            mediaRepository.delete(entity);
            log.debug("Media entity deleted: id={}", entity.getId());
            
        } catch (Exception e) {
            log.error("Failed to delete media entity: id={}", entity.getId(), e);
            throw e;
        }
    }

    /**
     * Verifica si existe un media con el mediaId dado.
     * 
     * @param mediaId ID del media
     * @return true si existe
     */
    @Transactional(readOnly = true)
    public boolean existsByMediaId(String mediaId) {
        boolean exists = mediaRepository.findByMediaId(mediaId).isPresent();
        log.debug("Media exists check: mediaId={}, exists={}", mediaId, exists);
        return exists;
    }
    
    /**
     * Cuenta el total de medias en el sistema.
     * 
     * @return Cantidad total de medias
     */
    @Transactional(readOnly = true)
    public long count() {
        long count = mediaRepository.count();
        log.debug("Total media count: {}", count);
        return count;
    }

    /**
     * Obtiene todos los medias de un usuario (como emisor o receptor).
     * 
     * @param userId ID del usuario
     * @return Lista de medias del usuario
     */
    @Transactional(readOnly = true)
    public List<MediaEntity> findAllByUser(String userId) {
        log.debug("Finding all media for user: userId={}", userId);
        
        List<MediaEntity> userMedia = mediaRepository.findAllByUser(userId);
        log.debug("Found {} media for user: userId={}", userMedia.size(), userId);
        
        return userMedia;
    }

}
