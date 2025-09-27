package com.basic_chat.profile_service.models;

import java.time.LocalDate;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.JoinColumn;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "perfiles")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Profile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId; // sera el valor de id de la tabla users
    private String profileName; // sera el valor de username de la tabla users
    private String profilePictureUrl;
    private String bio;
    @JsonFormat(pattern = "dd-MM-yyyy")
    private LocalDate dateOfBirth;

    // usuario actual marco a otros como favorito
    @ManyToMany
    @JoinTable(name = "user_favorites", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "favorite_id"))
    private Set<Profile> favorites;
}
