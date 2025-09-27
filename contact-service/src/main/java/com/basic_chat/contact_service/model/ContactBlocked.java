package com.basic_chat.contact_service.model;

import java.time.LocalDateTime;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "contactos_bloqueados")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ContactBlocked {
    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long blockedUserId;
    private LocalDateTime blockedAt;
}
