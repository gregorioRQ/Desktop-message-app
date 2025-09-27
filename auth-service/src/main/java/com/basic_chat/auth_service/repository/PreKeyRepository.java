package com.basic_chat.auth_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.basic_chat.auth_service.model.Prekey;

@Repository
public interface PreKeyRepository extends JpaRepository<Prekey, Long> {
    /*
     * Recupera el primer Prekey no utilizado para un usuario específico, ordenado
     * por ID de Prekey ascendente.
     * 
     * @param userId ID del usuario para el cual se busca el Prekey.
     * 
     * @return Un Optional que contiene el Prekey encontrado, o vacío si no hay
     * Prekeys disponibles.
     * 
     * @throws IllegalArgumentException si no se encuentra ningún Prekey
     * disponible para el usuario.
     */
    Prekey findByRegistrationId(int registrationId);

}
