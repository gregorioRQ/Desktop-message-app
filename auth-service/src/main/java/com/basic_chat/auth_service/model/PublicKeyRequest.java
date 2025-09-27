package com.basic_chat.auth_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PublicKeyRequest {
    private String userId;
    private String publicKey;
    private int registrationId;

}
