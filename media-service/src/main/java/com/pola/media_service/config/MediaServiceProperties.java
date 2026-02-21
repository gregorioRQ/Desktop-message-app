package com.pola.media_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Propiedades de configuración para el servicio de medios.
 * Permite externalizar configuración en application.yml
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "media")
@Validated
public class MediaServiceProperties {
    
    /**
     * Configuración de almacenamiento
     */
    private Storage storage = new Storage();
    
    /**
     * Configuración de procesamiento de imágenes
     */
    private Processing processing = new Processing();
    
    /**
     * Configuración de limpieza automática
     */
    private Cleanup cleanup = new Cleanup();
    
    @Data
    public static class Storage {
        /**
         * Ruta base donde se almacenan las imágenes
         * Ejemplo: /storage/media o /var/app/media
         */
        @NotBlank(message = "Base path cannot be empty")
        private String basePath = "/storage/media";
        
        /**
         * Tamaño máximo de archivo en MB
         */
        @Min(1)
        @Max(100)
        private int maxSizeMb = 28;
        
        /**
         * Crear directorios automáticamente si no existen
         */
        private boolean autoCreateDirectories = true;
    }
    
    @Data
    public static class Processing {
        /**
         * Ancho máximo de imagen en píxeles
         */
        @Min(100)
        @Max(4096)
        private int maxImageWidth = 1920;
        
        /**
         * Alto máximo de imagen en píxeles
         */
        @Min(100)
        @Max(4096)
        private int maxImageHeight = 1920;
        
        /**
         * Tamaño del thumbnail en píxeles (cuadrado)
         */
        @Min(50)
        @Max(500)
        private int thumbnailSize = 100;
        
        /**
         * Calidad de compresión WebP (0.0 - 1.0)
         */
        @Min(0)
        @Max(1)
        private float webpQuality = 0.85f;
        
        /**
         * Calidad de compresión para thumbnail (0.0 - 1.0)
         */
        @Min(0)
        @Max(1)
        private float thumbnailQuality = 0.70f;
        
        /**
         * Formatos de imagen permitidos
         */
        @NotNull
        private String[] allowedFormats = {"jpg", "jpeg", "png", "webp"};
    }
    
    @Data
    public static class Cleanup {
        /**
         * Habilitar limpieza automática de archivos entregados
         */
        private boolean enabled = true;
        
        /**
         * Días después de entrega para eliminar archivos
         */
        @Min(1)
        private int daysAfterDelivery = 7;
        
        /**
         * Expresión cron para ejecutar limpieza
         * Por defecto: todos los días a las 2 AM
         */
        private String cronExpression = "0 0 2 * * *";
    }
}