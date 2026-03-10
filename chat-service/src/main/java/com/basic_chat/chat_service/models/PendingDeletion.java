package com.basic_chat.chat_service.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pending_deletions")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PendingDeletion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String recipient;
    private String messageId; // ID del mensaje a eliminar
    private String deletedBy;  // Username del usuario que eliminó el mensaje (para enviar en la notificación)
}