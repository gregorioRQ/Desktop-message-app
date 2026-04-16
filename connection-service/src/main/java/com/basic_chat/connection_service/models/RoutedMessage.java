package com.basic_chat.connection_service.models;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
public class RoutedMessage {
    private String sender;
    private String recipient;
    private byte[] content;
    private String targetInstance;

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

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public String getTargetInstance() {
        return targetInstance;
    }

    public void setTargetInstance(String targetInstance) {
        this.targetInstance = targetInstance;
    }
}
