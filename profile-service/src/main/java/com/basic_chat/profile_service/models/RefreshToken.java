package com.basic_chat.profile_service.models;

import java.time.LocalDateTime;
// Entidad para refresh tokens


import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String token;
    private String userId;
    private LocalDateTime expiryDate;
    private String deviceId; // Identificador del dispositivo
    private LocalDateTime createdAt;
}
