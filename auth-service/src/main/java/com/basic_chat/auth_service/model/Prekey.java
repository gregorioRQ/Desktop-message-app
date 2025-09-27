package com.basic_chat.auth_service.model;

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
@Table(name = "prekeys")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Prekey {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private int registrationId;
    @Column(nullable = false)
    private int prekeyId; // el id original de libsignal
    @Column(nullable = false)
    private byte[] prekey; // parte publica de la prekey
    @Column(nullable = false)
    private boolean isUsed = false; // si esta prekey ha sido usada.
    @Column(nullable = false)
    private String timestamp; // creación de este registro.

}
