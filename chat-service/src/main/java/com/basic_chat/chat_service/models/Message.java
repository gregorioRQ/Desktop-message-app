package com.basic_chat.chat_service.models;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "mensajes")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sender; // username del emisor del mensaje
    private String receiver; // username del receptor del mensaje/dueño
    private byte[] content;
    private LocalDateTime created_at;
    private boolean isSeen;
    private String imageUrl; // URL de la imagen asociada al mensaje
}
