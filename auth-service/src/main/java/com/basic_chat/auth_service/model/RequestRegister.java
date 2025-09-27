package com.basic_chat.auth_service.model;

import lombok.Data;

@Data
public class RequestRegister {
    private String username;
    private int registrationId;
    private int deviceId;
    private int preKeyId;
    private byte[] preKeyPublic;
    private int signedPreKeyId;
    private byte[] signedPreKeyPublic;
    private byte[] signedPreKeySignature;
    private byte[] identityKey;
}
