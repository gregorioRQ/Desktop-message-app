package com.basic_chat.notifiation_service.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Entidad que representa la relación de contacto entre usuarios.
 * 
 * Almacena los contactos confirmados del usuario en el sistema de notificaciones.
 * Solo los contactos confirmados (is_confirmed = true) recibirán notificaciones
 * de presencia cuando el contacto se conecte/desconecte.
 * 
 * Esta entidad soporta el flujo de contactos fantasmas:
 * - Cuando un usuario agrega a otro, inicialmente is_confirmed = false
 * - Cuando el otro usuario confirma, se actualiza a is_confirmed = true
 * - Solo los contactos confirmados ven el estado online/offline del contacto
 */
@Entity
@Table(name = "user_contacts", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "contact_id"})
})
public class UserContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * El usuario propietario del contacto.
     * Relación many-to-one con la tabla users.
     */
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * El usuario de contacto.
     * Relación many-to-one con la tabla users.
     */
    @ManyToOne
    @JoinColumn(name = "contact_id", nullable = false)
    private User contact;

    /**
     * Username del contacto (para facilitar búsquedas sin joins).
     * Se almacena para evitar consultas frecuentes a la tabla user.
     */
    @Column(name = "contact_username", nullable = false)
    private String contactUsername;

    /**
     * Indica si el contacto ha sido confirmado por el usuario.
     * - false: Contacto fantasma (pendiente de confirmación)
     * - true: Contacto confirmado (recibe notificaciones de presencia)
     * 
     * Esta columna implementa la capa de privacidad:
     * Los contactos no confirmados no ven cuando el usuario está online.
     */
    @Column(name = "is_confirmed", nullable = false)
    private boolean isConfirmed = false;

    /**
     * Fecha de creación del contacto.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public UserContact() {
    }

    public UserContact(User user, User contact, String contactUsername) {
        this.user = user;
        this.contact = contact;
        this.contactUsername = contactUsername;
        this.isConfirmed = false;
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

    public String getContactUsername() {
        return contactUsername;
    }

    public void setContactUsername(String contactUsername) {
        this.contactUsername = contactUsername;
    }

    public boolean isConfirmed() {
        return isConfirmed;
    }

    public void setConfirmed(boolean confirmed) {
        isConfirmed = confirmed;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
