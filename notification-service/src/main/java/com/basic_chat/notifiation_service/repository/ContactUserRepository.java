package com.basic_chat.notifiation_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.basic_chat.notifiation_service.model.ContactUser;

@Repository
public interface ContactUserRepository extends JpaRepository<ContactUser, String> {

    /**
     * Busca todos los contactos de un usuario por su ID.
     * @param userId ID del usuario propietario de los contactos
     * @return Lista de contactos del usuario
     */
    List<ContactUser> findByUserId(String userId);

    /**
     * Busca todos los usuarios que tienen a un username específico como contacto.
     * Usado para notificar a los contactos sobre cambios de presencia.
     * @param contactUsername username del contacto
     * @return Lista de usuarios que tienen este contacto
     */
    List<ContactUser> findByContactUsername(String contactUsername);

    /**
     * Verifica si existe una relación de contacto entre un usuario y un username.
     * @param userId ID del usuario
     * @param contactUsername username del contacto
     * @return true si existe la relación
     */
    boolean existsByUserIdAndContactUsername(String userId, String contactUsername);

    /**
     * Busca los contactos de un usuario que están actualmente online.
     * Este método realiza un JOIN con la tabla de usuarios para filtrar
     * solo aquellos contactos cuyo estado online sea true.
     * 
     * Nota: La consulta asume que existe una entidad User con campo online
     * y que ContactUser tiene una relación o propiedad para obtener el username.
     * 
     * @param userId ID del usuario propietario
     * @return Lista de usernames de contactos que están online
     */
    @Query("SELECT cu.contactUsername FROM ContactUser cu JOIN User u ON cu.contactUsername = u.username WHERE cu.userId = :userId AND u.online = true")
    List<String> findOnlineContactUsernamesByUserId(@Param("userId") String userId);
}