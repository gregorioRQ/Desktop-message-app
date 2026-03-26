package com.basic_chat.connection_service.models;

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

    public static NotificationEvent createNewMessageEvent(String sender, String recipient, String recipientUserId, String messageId, byte[] data) {
        NotificationEvent event = new NotificationEvent();
        event.setType("NEW_MESSAGE");
        event.setMessageId(messageId);
        event.setSender(sender);
        event.setRecipient(recipient);
        event.setRecipientUserId(recipientUserId);
        event.setData(data);
        return event;
    }
}
