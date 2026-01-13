package com.basic_chat.chat_service.models;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pending_unblocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingUnblock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String blocker;      // Usuario que realizó el desbloqueo
    private String unblockedUser; // Usuario que fue desbloqueado (destinatario)
    private Long timestamp;
}