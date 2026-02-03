package com.basic_chat.notifiation_service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "user_contacts", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "contact_id"})
})
public class UserContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // El dueño del contacto
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // El contacto
    @ManyToOne
    @JoinColumn(name = "contact_id", nullable = false)
    private User contact;

    public UserContact() {
    }

    public UserContact(User user, User contact) {
        this.user = user;
        this.contact = contact;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public User getContact() {
        return contact;
    }

    public void setContact(User contact) {
        this.contact = contact;
    }
}
