package com.pola.media_service.scheduled;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.pola.media_service.config.MediaServiceProperties;
import com.pola.media_service.service.MediaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tarea programada para limpieza automática de medias antiguos.
 * Se ejecuta según la configuración en application.yml
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "media.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class MediaCleanupScheduledTask {
    private final MediaService mediaService;
    private final MediaServiceProperties properties;
    
    /**
     * Ejecuta limpieza de medias entregados hace más de X días.
     * Por defecto se ejecuta todos los días a las 2 AM.
     */
    @Scheduled(cron = "${media.cleanup.cron-expression:0 0 2 * * *}")
    public void cleanupOldMedia() {
        log.info("Starting scheduled media cleanup task");
        
        try {
            int daysAfterDelivery = properties.getCleanup().getDaysAfterDelivery();
            int deletedCount = mediaService.cleanupDeliveredMedia(daysAfterDelivery);
            
            log.info("Scheduled media cleanup completed: {} files deleted", deletedCount);
            
        } catch (Exception e) {
            log.error("Scheduled media cleanup failed", e);
        }
    }
}
