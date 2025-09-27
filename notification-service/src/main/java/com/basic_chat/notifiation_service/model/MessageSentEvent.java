package com.basic_chat.notifiation_service.model;

public class MessageSentEvent {
    private String sender;
    private String receiver;

    public MessageSentEvent() {
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
