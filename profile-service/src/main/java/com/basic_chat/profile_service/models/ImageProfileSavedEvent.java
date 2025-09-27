package com.basic_chat.profile_service.models;

import java.time.LocalDateTime;

public class ImageProfileSavedEvent {
    private Long id;
    private String url;
    private String originalFileName;
    private String contentType;
    private Long size;
    private Long userId;
    private String storedFileName;
    private LocalDateTime uploadedAt;

    public ImageProfileSavedEvent(Long id, String url, String originalFileName, String contentType, Long size,
            Long userId, String storedFileName, LocalDateTime uploadedAt) {
        this.id = id;
        this.url = url;
        this.originalFileName = originalFileName;
        this.contentType = contentType;
        this.size = size;
        this.userId = userId;
        this.storedFileName = storedFileName;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStoredFileName() {
        return storedFileName;
    }

    public void setStoredFileName(String storedFileName) {
        this.storedFileName = storedFileName;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }
}
