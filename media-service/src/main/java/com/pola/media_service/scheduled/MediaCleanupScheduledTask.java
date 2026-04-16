package com.pola.media_service.scheduled;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pola.media_service.config.MediaServiceProperties;
import com.pola.media_service.service.MediaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tarea programada para limpieza automática de medios.
 * Se ejecuta según la configuración en application.properties
 * 
 * Funciones:
 * 1. cleanupOldMedia: Elimina imágenes nunca descargadas después de X días
 * 2. cleanupPendingDeletion: Elimina imágenes descargadas después del período de gracia
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "media.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class MediaCleanupScheduledTask {
    private final MediaService mediaService;
    private final MediaServiceProperties properties;
    
    /**
     * Limpia medios nunca descargados después de X días.
     * Ejecución: según cron-expression (por defecto cada 3 horas)
     */
    @Scheduled(cron = "${media.cleanup.cron-expression:0 0 */3 * * *}")
    public void cleanupOldMedia() {
        log.info("Starting scheduled media cleanup (never downloaded)");
        
        try {
            int daysAfterDelivery = properties.getCleanup().getDaysAfterDelivery();
            int deletedCount = mediaService.cleanupDeliveredMedia(daysAfterDelivery);
            
            log.info("Scheduled media cleanup completed: {} files deleted", deletedCount);
            
        } catch (Exception e) {
            log.error("Scheduled media cleanup failed", e);
        }
    }
    
    /**
     * Limpia medios pendientes de eliminación tras el período de gracia.
     * Se ejecuta cada hora para verificar si hay medios listos para borrar.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void cleanupPendingDeletion() {
        log.info("Starting pending deletion cleanup");
        
        try {
            int deletedCount = mediaService.cleanupPendingDeletion();
            
            log.info("Pending deletion cleanup completed: {} files deleted", deletedCount);
            
        } catch (Exception e) {
            log.error("Pending deletion cleanup failed", e);
        }
    }
}
