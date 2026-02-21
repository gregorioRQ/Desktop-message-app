package com.pola.media_service.exception;

/**
 * Excepción lanzada cuando falla una validación de negocio.
 */
public class ValidationException extends MediaException {
    
    public ValidationException(String message) {
        super(message, "VALIDATION_ERROR");
    }
}