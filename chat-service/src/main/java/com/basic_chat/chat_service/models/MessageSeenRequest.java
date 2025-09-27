package com.basic_chat.chat_service.models;

import java.util.List;

public class MessageSeenRequest {
    private String receiver;
    private List<Long> messageIds;

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public List<Long> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<Long> messageIds) {
        this.messageIds = messageIds;
    }
}
