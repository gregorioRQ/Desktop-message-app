package com.basic_chat.chat_service.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserPresenceEvent implements Serializable {
    private String userId;
    private String username;
    private boolean online;
    private long timestamp;
}