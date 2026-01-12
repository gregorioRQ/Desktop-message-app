package com.basic_chat.chat_service.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "contact_blocks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"blocker", "blocked"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContactBlock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String blocker; // El usuario que realiza el bloqueo

    @Column(nullable = false)
    private String blocked; // El usuario que es bloqueado

    private LocalDateTime createdAt;

    public ContactBlock(String blocker, String blocked) {
        this.blocker = blocker;
        this.blocked = blocked;
        this.createdAt = LocalDateTime.now();
    }
}

