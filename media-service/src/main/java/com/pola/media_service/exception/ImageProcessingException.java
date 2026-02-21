package com.pola.media_service.exception;

/**
 * Excepción lanzada cuando falla el procesamiento de una imagen.
 */
public class ImageProcessingException extends MediaException {
    
    public ImageProcessingException(String message) {
        super(message, "IMAGE_PROCESSING_ERROR");
    }
    
    public ImageProcessingException(String message, Throwable cause) {
        super(message, "IMAGE_PROCESSING_ERROR", cause);
    }
}