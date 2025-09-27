package com.basic_chat.auth_service.model;

import lombok.Data;

@Data
public class SignedPreKeyRequest {
    public String userId;
    public int signedId;
    public String signedPreKeyPublicBase64;
    public String signedPreKeySignatureBase64;
    public int registrationId;
    public String timestamp;

}
