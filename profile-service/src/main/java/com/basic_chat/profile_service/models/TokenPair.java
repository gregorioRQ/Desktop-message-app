package com.basic_chat.profile_service.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TokenPair {
    private String accessToken;  // JWT corta duración (15-30 min)
    private String refreshToken; // Token larga duración (7-30 días)
}
