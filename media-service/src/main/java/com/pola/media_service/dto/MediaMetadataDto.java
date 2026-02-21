package com.pola.media_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para transferir metadata de un media entre capas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaMetadataDto {
    private String mediaId;
    private String senderId;
    private String receiverId;
    private String thumbnailUrl;
    private String fullImageUrl;
    private Long thumbnailSize;
    private Long fullImageSize;
    private Integer width;
    private Integer height;
}
