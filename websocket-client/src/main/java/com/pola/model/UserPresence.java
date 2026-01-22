package com.pola.model;

public class UserPresence {
    private String type;
    private String userId;

    public UserPresence() {
    }

    public UserPresence(String type, String userId) {
        this.type = type;
        this.userId = userId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public String toString() {
        return "UserPresence{" +
                "type='" + type + '\'' +
                ", userId='" + userId + '\'' +
                '}';
    }
}

