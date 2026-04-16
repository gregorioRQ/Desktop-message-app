package com.pola.media_service.controller;

import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.google.protobuf.ByteString;
import com.pola.media_service.proto.UploadImageRequest;
import com.pola.media_service.proto.UploadImageResponse;
import com.pola.media_service.service.MediaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Slf4j
public class TestMediaController {

    @Autowired
    private MediaService mediaService;

    @PostMapping("/upload-image")
    public ResponseEntity<UploadImageResponseDto> uploadImage(@RequestBody UploadImageRequestDto request) {
        log.info("Recibida solicitud de prueba - userId: {}, receiverId: {}", 
            request.getUserId(), request.getReceiverId());

        try {
            byte[] imageBytes = Base64.getDecoder().decode(request.getImageData());

            UploadImageRequest protobufRequest = UploadImageRequest.newBuilder()
                .setUserId(request.getUserId())
                .setReceiverId(request.getReceiverId())
                .setImageData(ByteString.copyFrom(imageBytes))
                .setOriginalFilename(request.getOriginalFilename())
                .setOriginalWidth(request.getOriginalWidth())
                .setOriginalHeight(request.getOriginalHeight())
                .build();

            UploadImageResponse response = mediaService.uploadImage(protobufRequest);
            
            UploadImageResponseDto dto = new UploadImageResponseDto();
            dto.setSuccess(response.getSuccess());
            dto.setMediaId(response.getMediaId());
            dto.setFullImageUrl(response.getFullImageUrl());
            dto.setFileSize(response.getFullImageSize());
            dto.setErrorMessage(response.getErrorMessage());
            
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            log.error("Error al decodificar base64: {}", e.getMessage());
            UploadImageResponseDto errorDto = new UploadImageResponseDto();
            errorDto.setSuccess(false);
            errorDto.setErrorMessage("Invalid base64 data: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorDto);
        } catch (Exception e) {
            log.error("Error al subir imagen: {}", e.getMessage());
            UploadImageResponseDto errorDto = new UploadImageResponseDto();
            errorDto.setSuccess(false);
            errorDto.setErrorMessage("Server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(errorDto);
        }
    }

    public static class UploadImageRequestDto {
        private String userId;
        private String receiverId;
        private String imageData;
        private String originalFilename;
        private int originalWidth;
        private int originalHeight;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getReceiverId() { return receiverId; }
        public void setReceiverId(String receiverId) { this.receiverId = receiverId; }
        public String getImageData() { return imageData; }
        public void setImageData(String imageData) { this.imageData = imageData; }
        public String getOriginalFilename() { return originalFilename; }
        public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
        public int getOriginalWidth() { return originalWidth; }
        public void setOriginalWidth(int originalWidth) { this.originalWidth = originalWidth; }
        public int getOriginalHeight() { return originalHeight; }
        public void setOriginalHeight(int originalHeight) { this.originalHeight = originalHeight; }
    }

    public static class UploadImageResponseDto {
        private boolean success;
        private String mediaId;
        private String fullImageUrl;
        private long fileSize;
        private String errorMessage;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMediaId() { return mediaId; }
        public void setMediaId(String mediaId) { this.mediaId = mediaId; }
        public String getFullImageUrl() { return fullImageUrl; }
        public void setFullImageUrl(String fullImageUrl) { this.fullImageUrl = fullImageUrl; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
