package com.pola.media_service.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "media", indexes = {
    @Index(name = "idx_media_id", columnList = "media_id"),
    @Index(name = "idx_sender_receiver", columnList = "sender_id, receiver_id"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "media_id", unique = true, nullable = false, length = 36)
    private String mediaId;  // UUID
    
    @Column(name = "sender_id", nullable = false, length = 50)
    private String senderId;
    
    @Column(name = "receiver_id", nullable = false, length = 50)
    private String receiverId;
    
    @Column(name = "original_filename", length = 255)
    private String originalFilename;
    
    @Column(name = "full_image_path", nullable = false, length = 500)
    private String fullImagePath;  // /storage/media/user_123/media_abc_full.webp
    
    @Column(name = "full_image_size", nullable = false)
    private Long fullImageSize;  // Bytes
    
    @Column(name = "original_width", nullable = false)
    private Integer originalWidth;
    
    @Column(name = "original_height", nullable = false)
    private Integer originalHeight;
    
    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;  // "image/webp"
    
    @Column(name = "delivered", nullable = false)
    @Builder.Default
    private Boolean delivered = false;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;  // Opcional: borrar después de X días
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
