package com.basic_chat.auth_service.model;

import java.security.Timestamp;
import java.util.Date;

import jakarta.annotation.Generated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "signed_pre_keys")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class SignedPreKey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private byte[] signedPrekeyPublic; // parte publica de la clave.
    private byte[] signedPreKeySignature; // Firma de la clave publica efimera.
    private int registrationId; // ID de registro del dispositivo del usuario

    private boolean isActive; // Indica si el preclave firmado está activo
    private int signedPrekeyId; // id de la signed prekey.
    private String timestamp; // Marca de tiempo de creación del preclave firmado

}
