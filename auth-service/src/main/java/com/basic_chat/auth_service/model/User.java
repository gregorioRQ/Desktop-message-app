package com.basic_chat.auth_service.model;

import java.util.Collection;
import java.util.Date;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "usuarios")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    @Column(nullable = false, length = 250)
    private String password;
    @Column(unique = true, nullable = false, length = 50)
    private String email;
    @Column(nullable = false, length = 50)
    private String name;
    private int deviceId;
    private byte[] publicIdentityKey; // parte pública de la clave de identidad
    private int registrationId; // ID de registro del dispositivo del usuario
    private Date createdAt; // marca de tiempo de creación del registro
    private Date updateAt; // maraca de tiempo de actualización del registro

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAuthorities'");
    }

}
