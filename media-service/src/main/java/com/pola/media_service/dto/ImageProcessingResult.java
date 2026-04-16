package com.pola.media_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado del procesamiento de una imagen.
 * Contiene la imagen completa procesada.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageProcessingResult {
    
    /**
     * Datos de la imagen completa en formato WebP
     */
    private byte[] fullImageData;
    
    /**
     * Ancho de la imagen original
     */
    private int originalWidth;
    
    /**
     * Alto de la imagen original
     */
    private int originalHeight;
    
    /**
     * Tamaño de la imagen completa en bytes
     */
    private long fullImageSize;
    
    /**
     * Factor de compresión logrado (0.0 - 1.0)
     * Ejemplo: 0.85 significa que la imagen resultante es 15% del tamaño original
     */
    private float compressionRatio;
    
    /**
     * Indica si la imagen fue redimensionada
     */
    private boolean wasResized;
    
    /**
     * Formato original de la imagen
     */
    private String originalFormat;
}