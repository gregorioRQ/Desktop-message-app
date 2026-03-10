package com.basic_chat.chat_service.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutedMessageEvent {
    private String sender;
    private String recipient;
    private byte[] content;
    private String targetInstance;
}
