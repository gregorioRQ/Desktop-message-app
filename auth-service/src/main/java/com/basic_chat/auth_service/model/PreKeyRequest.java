package com.basic_chat.auth_service.model;

import java.util.Map;

import lombok.Data;

@Data
public class PreKeyRequest {
    private String userId;
    private Map<String, Object> preKeys;
}
