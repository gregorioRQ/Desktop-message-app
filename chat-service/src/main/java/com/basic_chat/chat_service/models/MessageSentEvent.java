package com.basic_chat.chat_service.models;

public class MessageSentEvent {
    // username del que envio el mensaje
    private String sender;
    // username del que recibio el mensaje
    private String receiver;

    public MessageSentEvent() {
    }

    public MessageSentEvent(String sender, String receiver) {
        this.sender = sender;
        this.receiver = receiver;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

}
