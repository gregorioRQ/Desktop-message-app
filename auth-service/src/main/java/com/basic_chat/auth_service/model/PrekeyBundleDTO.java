package com.basic_chat.auth_service.model;

import lombok.Data;

@Data
public class PrekeyBundleDTO {
    public int registrationId;
    public int deviceId;
    public int preKeyId;
    public byte[] preKeyPublic;
    public int signedPreKeyId;
    public byte[] signedPreKeyPublic;
    public byte[] signedPreKeySignature;
    public byte[] identityKey;
}
