package com.basic_chat.chat_service.models;

public class MessageSeenEvent {
    private Long messageId;
    private String sender;
    private String receiver;

    public MessageSeenEvent() {
    }

    public MessageSeenEvent(Long messageId, String sender, String receiver) {
        this.messageId = messageId;
        this.sender = sender;
        this.receiver = receiver;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

}
