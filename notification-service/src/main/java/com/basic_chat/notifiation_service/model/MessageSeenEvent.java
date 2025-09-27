package com.basic_chat.notifiation_service.model;

public class MessageSeenEvent {
    private String messageId;
    private String receiver;

    public MessageSeenEvent() {
    }

    public MessageSeenEvent(String messageId, String receiver) {
        this.messageId = messageId;
        this.receiver = receiver;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }
}
