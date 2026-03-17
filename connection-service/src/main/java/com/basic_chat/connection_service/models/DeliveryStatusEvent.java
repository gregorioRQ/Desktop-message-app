package com.basic_chat.connection_service.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryStatusEvent {
    private String type;
    private String messageId;
    private String recipient;
    private byte[] data;
}
