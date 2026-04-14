package com.basic_chat.notifiation_service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;

@Entity
@IdClass(ContactUserId.class)
@Table(name = "contact_users")
public class ContactUser {

    @Id
    private String userId;

    @Id
    private String contactUsername;

    public ContactUser() {
    }

    public ContactUser(String userId, String contactUsername) {
        this.userId = userId;
        this.contactUsername = contactUsername;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getContactUsername() {
        return contactUsername;
    }

    public void setContactUsername(String contactUsername) {
        this.contactUsername = contactUsername;
    }
}

class ContactUserId implements Serializable {
    private String userId;
    private String contactUsername;

    public ContactUserId() {
    }

    public ContactUserId(String userId, String contactUsername) {
        this.userId = userId;
        this.contactUsername = contactUsername;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactUserId that = (ContactUserId) o;
        return userId.equals(that.userId) && contactUsername.equals(that.contactUsername);
    }

    @Override
    public int hashCode() {
        int result = userId.hashCode();
        result = 31 * result + contactUsername.hashCode();
        return result;
    }
}