package com.pola.media_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.pola.media_service.proto.DownloadImageResponse;
import com.pola.media_service.proto.UploadImageResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Manejador global de excepciones.
 * Captura excepciones no manejadas y devuelve responses apropiados.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    /**
     * Maneja excepciones de validación.
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<byte[]> handleValidationException(ValidationException e) {
        log.warn("Validation error: {}", e.getMessage());
        
        UploadImageResponse response = UploadImageResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(e.getMessage())
            .build();
        
        return ResponseEntity
            .badRequest()
            .body(response.toByteArray());
    }
    
    /**
     * Maneja excepciones de media no encontrado.
     */
    @ExceptionHandler(MediaNotFoundException.class)
    public ResponseEntity<byte[]> handleMediaNotFoundException(MediaNotFoundException e) {
        log.warn("Media not found: {}", e.getMessage());
        
        DownloadImageResponse response = DownloadImageResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage(e.getMessage())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(response.toByteArray());
    }
    
    /**
     * Maneja excepciones de procesamiento de imagen.
     */
    @ExceptionHandler(ImageProcessingException.class)
    public ResponseEntity<byte[]> handleImageProcessingException(ImageProcessingException e) {
        log.error("Image processing error: {}", e.getMessage(), e);
        
        UploadImageResponse response = UploadImageResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage("Failed to process image: " + e.getMessage())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response.toByteArray());
    }
    
    /**
     * Maneja excepciones de almacenamiento.
     */
    @ExceptionHandler(StorageException.class)
    public ResponseEntity<byte[]> handleStorageException(StorageException e) {
        log.error("Storage error: {}", e.getMessage(), e);
        
        UploadImageResponse response = UploadImageResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage("Failed to store image: " + e.getMessage())
            .build();
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response.toByteArray());
    }
    
    /**
     * Maneja cualquier otra excepción no esperada.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<byte[]> handleGenericException(Exception e) {
        log.error("Unexpected error", e);
        
        UploadImageResponse response = UploadImageResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage("Internal server error")
            .build();
        
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(response.toByteArray());
    }
}
