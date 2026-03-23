package com.basic_chat.notifiation_service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String type;
    private String messageId;
    private String sender;
    private String recipient;
    private String recipientUserId;
    private byte[] data;
}
