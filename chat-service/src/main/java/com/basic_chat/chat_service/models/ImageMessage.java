package com.basic_chat.chat_service.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entidad que representa un mensaje de imagen pendiente.
 * Se guarda cuando el destinatario está offline y se entrega cuando se conecta.
 */
@Entity
@Table(name = "image_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String mediaId;
    private String senderId;
    private String receiverId;
    private String fullImageUrl;
    private Integer originalWidth;
    private Integer originalHeight;
    private Long fileSize;
    private Long timestamp;
    private boolean delivered;
}
