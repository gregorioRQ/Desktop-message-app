package com.pola.media_service.exception;
/**
 * Excepción lanzada cuando falla el almacenamiento en disco.
 */
public class StorageException extends MediaException {
    
    public StorageException(String message) {
        super(message, "STORAGE_ERROR");
    }
    
    public StorageException(String message, Throwable cause) {
        super(message, "STORAGE_ERROR", cause);
    }
}
