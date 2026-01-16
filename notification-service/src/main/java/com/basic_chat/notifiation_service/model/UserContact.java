package com.basic_chat.notifiation_service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_contacts")
public class UserContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String contactUsername;

    public UserContact() {
    }

    public UserContact(String username, String contactUsername) {
        this.username = username;
        this.contactUsername = contactUsername;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

