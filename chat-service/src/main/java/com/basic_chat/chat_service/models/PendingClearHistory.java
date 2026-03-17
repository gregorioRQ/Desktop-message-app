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
@Table(name = "pending_clear_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingClearHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sender;      // Usuario que solicitó la limpieza del historial
    private String recipient;  // Usuario que debe aplicar la limpieza en su cliente
    private Long timestamp;
}
