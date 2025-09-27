package com.basic_chat.auth_service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data
public class Prekeys {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long Id;
    private int preKeyId;
    private byte[] preKeyPublic;

    private int signedPreKeyId;
    private byte[] signedPreKeyPublic;
    private byte[] signedPreKeySignature;
}
