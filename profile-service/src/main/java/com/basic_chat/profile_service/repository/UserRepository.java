package com.basic_chat.profile_service.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.basic_chat.profile_service.models.User;

@Repository
public interface UserRepository extends JpaRepository<User, String>{
    /**
     * Busca un usuario por nombre de usuario
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Verifica si existe un usuario con el nombre dado
     */
    boolean existsByUsername(String username);
}
