package com.pola.media_service.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pola.media_service.proto.DownloadImageRequest;
import com.pola.media_service.proto.DownloadImageResponse;
import com.pola.media_service.proto.UploadImageRequest;
import com.pola.media_service.proto.UploadImageResponse;
import com.pola.media_service.service.MediaService;

@RestController
@RequestMapping("/api/v1/media")
public class MediaHttpController {
     @Autowired
    private MediaService mediaService;
    
    /**
     * Upload imagen completa (HTTP + Protobuf)
     * Content-Type: application/x-protobuf
     */
    @PostMapping(
        value = "/upload",
        consumes = "application/x-protobuf",
        produces = "application/x-protobuf"
    )
    public ResponseEntity<UploadImageResponse> uploadImage(@RequestBody UploadImageRequest request) {
        try {
            // TODO: Validar JWT del usuario (request.getUserId())
            
            // Procesar imagen (TODO: implementar en service)
            UploadImageResponse response = mediaService.uploadImage(request);
            
            // Devolver response Protobuf
            // Spring usa ProtobufHttpMessageConverter para serializar automáticamente
            return ResponseEntity.ok(response);
                
        } catch (Exception e) {
            // Error general
            UploadImageResponse errorResponse = UploadImageResponse.newBuilder()
                .setSuccess(false)
                .setErrorMessage("Server error: " + e.getMessage())
                .build();
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
    
    /**
     * Download imagen completa (HTTP + Protobuf)
     */
    @PostMapping(
        value = "/download",
        consumes = "application/x-protobuf",
        produces = "application/x-protobuf"
    )
    public ResponseEntity<DownloadImageResponse> downloadImage(@RequestBody DownloadImageRequest request) {
        try {
            // TODO: Validar que el usuario tenga permiso para descargar
            
            // Obtener imagen (TODO: implementar en service)
            DownloadImageResponse response = mediaService.downloadImage(request);
            
            if (!response.getSuccess()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            return ResponseEntity.ok(response);
                
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Endpoint alternativo usando path variable (más REST-like)
     */
    @GetMapping(value = "/download/{mediaId}", produces = "application/octet-stream")
    public ResponseEntity<byte[]> downloadImageById(
            @PathVariable String mediaId,
            @RequestHeader("X-User-Id") String userId) {
        
        try {
            DownloadImageRequest request = DownloadImageRequest.newBuilder()
                .setMediaId(mediaId)
                .setUserId(userId)
                .build();
            
            DownloadImageResponse response = mediaService.downloadImage(request);
            
            if (!response.getSuccess()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(response.getMimeType()))
                .body(response.getImageData().toByteArray());
                
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Media Service is running");
    }
}
