package com.pola.media_service.exception;

/**
 * Excepción lanzada cuando no se encuentra un media solicitado.
 */
public class MediaNotFoundException extends MediaException {
    
    public MediaNotFoundException(String mediaId) {
        super(String.format("Media not found: %s", mediaId), "NOT_FOUND");
    }
}
