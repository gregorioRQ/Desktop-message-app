package com.basic_chat.connection_service.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutedMessage {
    private String sender;
    private String recipient;
    private byte[] content;
    private String targetInstance;
}
