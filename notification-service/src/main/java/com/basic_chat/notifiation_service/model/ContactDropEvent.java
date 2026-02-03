package com.basic_chat.notifiation_service.model;

import java.util.List;

public class ContactDropEvent {
    private String userId;
    private List<String> contactIds;

    public ContactDropEvent() {
    }

    public ContactDropEvent(String userId, List<String> contactIds) {
        this.userId = userId;
        this.contactIds = contactIds;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public List<String> getContactIds() {
        return contactIds;
    }

    public void setContactIds(List<String> contactIds) {
        this.contactIds = contactIds;
    }
}

