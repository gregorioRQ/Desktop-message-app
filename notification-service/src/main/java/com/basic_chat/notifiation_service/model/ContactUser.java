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
    private String username;

    @Id
    private String contactUsername;

    public ContactUser() {
    }

    public ContactUser(String username, String contactUsername) {
        this.username = username;
        this.contactUsername = contactUsername;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getContactUsername() {
        return contactUsername;
    }

    public void setContactUsername(String contactUsername) {
        this.contactUsername = contactUsername;
    }
}

class ContactUserId implements Serializable {
    private String username;
    private String contactUsername;

    public ContactUserId() {
    }

    public ContactUserId(String username, String contactUsername) {
        this.username = username;
        this.contactUsername = contactUsername;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContactUserId that = (ContactUserId) o;
        return username.equals(that.username) && contactUsername.equals(that.contactUsername);
    }

    @Override
    public int hashCode() {
        int result = username.hashCode();
        result = 31 * result + contactUsername.hashCode();
        return result;
    }
}
