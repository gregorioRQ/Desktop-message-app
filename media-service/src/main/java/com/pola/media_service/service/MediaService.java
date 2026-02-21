package com.pola.media_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.protobuf.ByteString;
import com.pola.media_service.dto.ImageProcessingResult;
import com.pola.media_service.dto.MediaMetadataDto;
import com.pola.media_service.exception.MediaException;
import com.pola.media_service.exception.MediaNotFoundException;
import com.pola.media_service.model.MediaEntity;
import com.pola.media_service.proto.DownloadImageRequest;
import com.pola.media_service.proto.DownloadImageResponse;
import com.pola.media_service.proto.UploadImageRequest;
import com.pola.media_service.proto.UploadImageResponse;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.pola.media_service.constants.MediaConstants.*;

import java.util.List;
import java.util.UUID;

/**
 * Servicio principal de gestión de medios.
 * Orquesta las operaciones entre validación, procesamiento, almacenamiento y metadata.
 * 
 * Responsabilidades:
 * - Coordinar el flujo de upload de imágenes
 * - Coordinar el flujo de download de imágenes
 * - Gestionar el ciclo de vida de los medias
 * - Proporcionar operaciones de alto nivel
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {
    private final ValidationService validationService;
    private final ImageProcessingService imageProcessingService;
    private final FileStorageService fileStorageService;
    private final MetadataService metadataService;
    
    /**
     * Procesa y almacena una nueva imagen.
     * 
     * Flujo completo:
     * 1. Validar datos de entrada
     * 2. Procesar imagen (recomprimir, convertir a WebP, generar thumbnail)
     * 3. Almacenar en sistema de archivos
     * 4. Guardar metadata en base de datos
     * 5. Construir response con URLs
     * 
     * @param request Request Protobuf con la imagen y metadata
     * @return UploadImageResponse con URLs y metadata
     */
    @Transactional
    public UploadImageResponse uploadImage(UploadImageRequest request) {
        log.info(LOG_UPLOAD_START, request.getUserId(), request.getReceiverId());
        
        long startTime = System.currentTimeMillis();
        String mediaId = null;
        
        try {
            // PASO 1: Validar request
            byte[] imageData = request.getImageData().toByteArray();
            validationService.validateImageUpload(
                imageData, 
                request.getUserId(), 
                request.getReceiverId()
            );
            
            // PASO 2: Procesar imagen (recomprimir, WebP, thumbnail)
            ImageProcessingResult processingResult = imageProcessingService.processImage(
                imageData,
                request.getOriginalFilename()
            );
            
            // PASO 3: Generar ID único para el media
            mediaId = generateMediaId();
            log.debug("Generated mediaId: {}", mediaId);
            
            // PASO 4: Almacenar archivos en disco
            FileStorageService.StorageResult storageResult = fileStorageService.storeMedia(
                mediaId,
                request.getReceiverId(),
                processingResult.getThumbnailData(),
                processingResult.getFullImageData()
            );
            
            // PASO 5: Guardar metadata en base de datos
            MediaEntity mediaEntity = buildMediaEntity(
                mediaId,
                request,
                processingResult,
                storageResult
            );
            
            metadataService.saveMetadata(mediaEntity);
            
            // PASO 6: Construir response exitoso
            UploadImageResponse response = buildUploadResponse(
                mediaId,
                processingResult,
                true,
                null
            );
            
            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info(LOG_UPLOAD_SUCCESS + ", time={}ms", 
                mediaId, 
                processingResult.getFullImageSize() / BYTES_IN_KB,
                elapsedTime);
            
            return response;
            
        } catch (Exception e) {
            log.error(LOG_UPLOAD_FAILED, request.getUserId(), e.getMessage(), e);
            
            // Rollback: intentar limpiar archivos si se guardaron
            if (mediaId != null) {
                cleanupFailedUpload(mediaId, request.getReceiverId());
            }
            
            // Construir response de error
            return buildUploadResponse(null, null, false, e.getMessage());
        }
    }
    
  /**
     * Descarga una imagen del servidor.
     * 
     * Flujo:
     * 1. Validar permisos del usuario
     * 2. Buscar metadata en BD
     * 3. Leer archivo del disco
     * 4. Marcar como entregado (si es el receptor)
     * 5. Devolver bytes de la imagen
     * 
     * @param request Request con mediaId y userId
     * @return DownloadImageResponse con los bytes de la imagen
     */
    @Transactional
    public DownloadImageResponse downloadImage(DownloadImageRequest request) {
        log.info(LOG_DOWNLOAD_START, request.getMediaId(), request.getUserId());
        
        try {
            // PASO 1: Buscar metadata
            MediaEntity mediaEntity = metadataService.findByMediaId(request.getMediaId());
            
            // PASO 2: Validar permisos
            validationService.validateDownloadPermission(
                request.getUserId(),
                mediaEntity.getSenderId(),
                mediaEntity.getReceiverId()
            );
            
            // PASO 3: Verificar si ya fue entregado y eliminado
            if (mediaEntity.getDelivered() && 
                !fileStorageService.fileExists(mediaEntity.getFullImagePath())) {
                log.warn("Media already delivered and removed: mediaId={}", request.getMediaId());
                return buildDownloadErrorResponse(ERROR_ALREADY_DELIVERED);
            }
            
            // PASO 4: Leer archivo del disco
            byte[] imageData = fileStorageService.readFile(mediaEntity.getFullImagePath());
            
            // PASO 5: Si es el receptor, marcar como entregado
            if (request.getUserId().equals(mediaEntity.getReceiverId())) {
                metadataService.markAsDelivered(request.getMediaId());
                log.debug("Media marked as delivered: mediaId={}", request.getMediaId());
                
                // TODO: Programar eliminación automática según configuración
            }
            
            // PASO 6: Construir response
            DownloadImageResponse response = DownloadImageResponse.newBuilder()
                .setSuccess(true)
                .setImageData(ByteString.copyFrom(imageData))
                .setMimeType(mediaEntity.getMimeType())
                .setWidth(mediaEntity.getOriginalWidth())
                .setHeight(mediaEntity.getOriginalHeight())
                .build();
            
            log.info(LOG_DOWNLOAD_SUCCESS, request.getMediaId());
            
            return response;
            
        } catch (MediaNotFoundException e) {
            log.error(LOG_DOWNLOAD_FAILED, request.getMediaId(), e.getMessage());
            return buildDownloadErrorResponse(e.getMessage());
            
        } catch (Exception e) {
            log.error(LOG_DOWNLOAD_FAILED, request.getMediaId(), e.getMessage(), e);
            return buildDownloadErrorResponse("Failed to download image: " + e.getMessage());
        }
    }

    /**
     * Obtiene el thumbnail de un media.
     * Útil para enviar por WebSocket (archivo pequeño).
     * 
     * @param mediaId ID del media
     * @return Bytes del thumbnail
     * @throws MediaNotFoundException si no existe
     */
    @Transactional(readOnly = true)
    public byte[] getThumbnail(String mediaId) {
        log.debug("Getting thumbnail: mediaId={}", mediaId);
        
        MediaEntity mediaEntity = metadataService.findByMediaId(mediaId);
        byte[] thumbnailData = fileStorageService.readFile(mediaEntity.getThumbnailPath());
        
        log.debug("Thumbnail retrieved: mediaId={}, size={}KB", 
            mediaId, thumbnailData.length / BYTES_IN_KB);
        
        return thumbnailData;
    }
    
    /**
     * Marca un media como entregado.
     * Llamado desde el WebSocket handler cuando se envía exitosamente.
     * 
     * @param mediaId ID del media
     */
    @Transactional
    public void markAsDelivered(String mediaId) {
        log.info("Marking media as delivered: mediaId={}", mediaId);
        
        try {
            metadataService.markAsDelivered(mediaId);
            log.debug("Media marked as delivered successfully: mediaId={}", mediaId);
            
        } catch (Exception e) {
            log.error("Failed to mark media as delivered: mediaId={}", mediaId, e);
            // No lanzar excepción - no queremos fallar la entrega del mensaje
        }
    }

     /**
     * Elimina un media del servidor (archivos + metadata).
     * 
     * @param mediaId ID del media
     * @param userId ID del usuario que solicita (para validar permisos)
     * @throws MediaNotFoundException si no existe
     */
    @Transactional
    public void deleteMedia(String mediaId, String userId) {
        log.info("Deleting media: mediaId={}, requestedBy={}", mediaId, userId);
        
        try {
            // Buscar metadata
            MediaEntity mediaEntity = metadataService.findByMediaId(mediaId);
            
            // Validar permisos (solo emisor o receptor pueden eliminar)
            validationService.validateDownloadPermission(
                userId,
                mediaEntity.getSenderId(),
                mediaEntity.getReceiverId()
            );
            
            // Eliminar archivos del disco
            fileStorageService.deleteMedia(
                mediaEntity.getThumbnailPath(),
                mediaEntity.getFullImagePath()
            );
            
            // Eliminar metadata de BD
            metadataService.delete(mediaEntity);
            
            log.info(LOG_DELETE_SUCCESS, mediaId);
            
        } catch (Exception e) {
            log.error("Failed to delete media: mediaId={}", mediaId, e);
            throw new MediaException("Failed to delete media", e);
        }
    }

    /**
     * Obtiene los medias pendientes de entrega para un receptor.
     * Útil cuando un usuario se conecta y necesita descargar mensajes pendientes.
     * 
     * @param receiverId ID del receptor
     * @return Lista de DTOs con metadata
     */
    @Transactional(readOnly = true)
    public List<MediaMetadataDto> getPendingMediaForReceiver(String receiverId) {
        log.debug("Getting pending media for receiver: receiverId={}", receiverId);
        
        List<MediaEntity> pendingMedia = metadataService.findPendingMediaForReceiver(receiverId);
        
        log.debug("Found {} pending media for receiver: receiverId={}", 
            pendingMedia.size(), receiverId);
        
        return pendingMedia.stream()
            .map(this::mapToDto)
            .toList();
    }

    /**
     * Limpia medias antiguos ya entregados.
     * Ejecutado por scheduled task automáticamente.
     * 
     * @param daysAfterDelivery Días después de entrega para eliminar
     * @return Cantidad de medias eliminados
     */
    @Transactional
    public int cleanupDeliveredMedia(int daysAfterDelivery) {
        log.info(LOG_CLEANUP_START, daysAfterDelivery);
        
        try {
            List<MediaEntity> oldMedia = metadataService.findDeliveredMediaOlderThan(daysAfterDelivery);
            
            int deletedCount = 0;
            
            for (MediaEntity media : oldMedia) {
                try {
                    // Eliminar archivos
                    fileStorageService.deleteMedia(
                        media.getThumbnailPath(),
                        media.getFullImagePath()
                    );
                    
                    // Eliminar metadata
                    metadataService.delete(media);
                    
                    deletedCount++;
                    log.debug("Cleaned up media: mediaId={}", media.getMediaId());
                    
                } catch (Exception e) {
                    log.error("Failed to cleanup media: mediaId={}", media.getMediaId(), e);
                    // Continuar con los demás
                }
            }
            
            log.info(LOG_CLEANUP_COMPLETE, deletedCount);
            return deletedCount;
            
        } catch (Exception e) {
            log.error("Cleanup task failed", e);
            throw new MediaException("Cleanup task failed", e);
        }
    }

    // ==================== MÉTODOS PRIVADOS (Helpers) ====================
    
    /**
     * Genera un ID único para un media.
     * 
     * @return UUID como String
     */
    private String generateMediaId() {
        return UUID.randomUUID().toString();
    }
    
    /**
     * Construye una entidad MediaEntity a partir de los datos procesados.
     * 
     * @param mediaId ID único generado
     * @param request Request original
     * @param processingResult Resultado del procesamiento
     * @param storageResult Resultado del almacenamiento
     * @return MediaEntity lista para guardar
     */
    private MediaEntity buildMediaEntity(String mediaId,
                                          UploadImageRequest request,
                                          ImageProcessingResult processingResult,
                                          FileStorageService.StorageResult storageResult) {
        return MediaEntity.builder()
            .mediaId(mediaId)
            .senderId(request.getUserId())
            .receiverId(request.getReceiverId())
            .originalFilename(request.getOriginalFilename())
            .thumbnailPath(storageResult.thumbnailPath())
            .fullImagePath(storageResult.fullImagePath())
            .thumbnailSize(processingResult.getThumbnailSize())
            .fullImageSize(processingResult.getFullImageSize())
            .originalWidth(processingResult.getOriginalWidth())
            .originalHeight(processingResult.getOriginalHeight())
            .mimeType(MIME_TYPE_WEBP)
            .delivered(false)
            .build();
    }
    
    /**
     * Construye response de upload (exitoso o con error).
     * 
     * @param mediaId ID del media (null si falló)
     * @param processingResult Resultado del procesamiento (null si falló)
     * @param success Indica si fue exitoso
     * @param errorMessage Mensaje de error (null si exitoso)
     * @return UploadImageResponse
     */
    private UploadImageResponse buildUploadResponse(String mediaId,
                                                      ImageProcessingResult processingResult,
                                                      boolean success,
                                                      String errorMessage) {
        UploadImageResponse.Builder builder = UploadImageResponse.newBuilder()
            .setSuccess(success);
        
        if (success && mediaId != null && processingResult != null) {
            builder.setMediaId(mediaId)
                   .setThumbnailUrl(String.format("/api/media/download/%s/thumbnail", mediaId))
                   .setFullImageUrl(String.format("/api/media/download/%s", mediaId))
                   .setThumbnailSize(processingResult.getThumbnailSize())
                   .setFullImageSize(processingResult.getFullImageSize());
        } else if (errorMessage != null) {
            builder.setErrorMessage(errorMessage);
        }
        
        return builder.build();
    }
    
    /**
     * Construye response de download con error.
     * 
     * @param errorMessage Mensaje de error
     * @return DownloadImageResponse con error
     */
    private DownloadImageResponse buildDownloadErrorResponse(String errorMessage) {
        return DownloadImageResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(errorMessage)
            .build();
    }
    
    /**
     * Limpia archivos de un upload fallido.
     * Intenta eliminar archivos que pudieron haberse guardado antes del error.
     * 
     * @param mediaId ID del media fallido
     * @param receiverId ID del receptor (para ubicar el directorio)
     */
    private void cleanupFailedUpload(String mediaId, String receiverId) {
        log.warn("Cleaning up failed upload: mediaId={}", mediaId);
        
        try {
            // Intentar eliminar archivos si existen
            String basePath = fileStorageService.getClass()
                .getDeclaredMethod("createUserDirectory", Long.class)
                .invoke(fileStorageService, receiverId)
                .toString();
            
            String thumbnailPath = basePath + "/" + mediaId + SUFFIX_THUMBNAIL + EXTENSION_WEBP;
            String fullImagePath = basePath + "/" + mediaId + SUFFIX_FULL + EXTENSION_WEBP;
            
            fileStorageService.deleteMedia(thumbnailPath, fullImagePath);
            
            log.debug("Failed upload cleaned up: mediaId={}", mediaId);
            
        } catch (Exception e) {
            log.error("Failed to cleanup failed upload: mediaId={}", mediaId, e);
            // No lanzar excepción - es solo cleanup
        }
    }
    
    /**
     * Mapea MediaEntity a DTO para transferencia.
     * 
     * @param entity Entidad de BD
     * @return DTO con datos relevantes
     */
    private MediaMetadataDto mapToDto(MediaEntity entity) {
        return MediaMetadataDto.builder()
            .mediaId(entity.getMediaId())
            .senderId(entity.getSenderId())
            .receiverId(entity.getReceiverId())
            .thumbnailUrl(String.format("/api/media/download/%s/thumbnail", entity.getMediaId()))
            .fullImageUrl(String.format("/api/media/download/%s", entity.getMediaId()))
            .thumbnailSize(entity.getThumbnailSize())
            .fullImageSize(entity.getFullImageSize())
            .width(entity.getOriginalWidth())
            .height(entity.getOriginalHeight())
            .build();
    }
}
