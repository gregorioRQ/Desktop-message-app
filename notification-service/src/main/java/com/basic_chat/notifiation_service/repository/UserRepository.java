package com.basic_chat.notifiation_service.repository;

import com.basic_chat.notifiation_service.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findById(String id);
    
    /**
     * Busca un usuario por su username.
     * 
     * @param username El nombre de usuario a buscar
     * @return Optional con el usuario si existe
     */
    Optional<User> findByUsername(String username);
}