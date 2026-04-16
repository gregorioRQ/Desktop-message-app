package com.basic_chat.connection_service.models;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String type;
    private String messageId;
    private String sender;
    private String recipient;
    private String recipientUserId;
    private byte[] data;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getRecipientUserId() {
        return recipientUserId;
    }

    public void setRecipientUserId(String recipientUserId) {
        this.recipientUserId = recipientUserId;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

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

    public static NotificationEvent createNewImageMessageEvent(String sender, String receiverUserId, byte[] imageData) {
        NotificationEvent event = new NotificationEvent();
        event.setType("NEW_IMAGE_MESSAGE");
        event.setSender(sender);
        event.setRecipientUserId(receiverUserId);
        event.setData(imageData);
        return event;
    }
}
