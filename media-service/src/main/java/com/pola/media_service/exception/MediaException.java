package com.pola.media_service.exception;


/**
 * Excepción base para todas las excepciones del servicio de medios.
 * Permite captura global y manejo consistente.
 */
public class MediaException extends RuntimeException {
    
    private final String errorCode;
    
    public MediaException(String message) {
        super(message);
        this.errorCode = "MEDIA_ERROR";
    }
    
    public MediaException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "MEDIA_ERROR";
    }
    
    public MediaException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public MediaException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}