package com.pola.media_service.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pola.media_service.proto.DownloadImageRequest;
import com.pola.media_service.proto.DownloadImageResponse;
import com.pola.media_service.proto.UploadImageRequest;
import com.pola.media_service.proto.UploadImageResponse;
import com.pola.media_service.repository.MediaRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class MediaService {
    @Autowired
    private MediaRepository mediaRepository;
    
    // TODO: Implementar lógica completa
    
    public UploadImageResponse uploadImage(UploadImageRequest request) {
        // TODO: Implementar procesamiento de imagen
        log.info("Upload image request from user {}", request.getUserId());
        
        return UploadImageResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage("Not implemented yet")
            .build();
    }
    
    public DownloadImageResponse downloadImage(DownloadImageRequest request) {
        // TODO: Implementar descarga
        log.info("Download image request: mediaId={}, userId={}", 
            request.getMediaId(), request.getUserId());
        
        return DownloadImageResponse.newBuilder()
            .setSuccess(false)
            .setErrorMessage("Not implemented yet")
            .build();
    }
    
    public void markAsDelivered(String mediaId) {
        // TODO: Actualizar BD
        log.info("Marking media as delivered: {}", mediaId);
    }
}
