package com.basic_chat.notifiation_service.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.basic_chat.notifiation_service.model.User;
import com.basic_chat.notifiation_service.model.UserContact;

/**
 * Repository para gestionar los contactos de usuarios en el servicio de notificaciones.
 * 
 * Proporciona métodos para consultar contactos confirmados y no confirmados.
 * Solo los contactos confirmados reciben notificaciones de presencia.
 */
public interface UserContactRepository extends JpaRepository<UserContact, Long> {

    List<UserContact> findByUser(User user);

    List<UserContact> findByUserAndIsConfirmedTrue(User user);

    /**
     * Obtiene contactos confirmados de un usuario por su ID.
     * 
     * @param userId El ID del usuario propietario
     * @return Lista de contactos confirmados
     */
    @Query("SELECT uc FROM UserContact uc WHERE uc.user.id = :userId AND uc.isConfirmed = true")
    List<UserContact> findByUserIdAndIsConfirmedTrue(@Param("userId") String userId);

    Optional<UserContact> findByUserAndContact(User user, User contact);

    Optional<UserContact> findByUserIdAndContactUsername(String userId, String contactUsername);

    @Query("SELECT uc FROM UserContact uc WHERE uc.contact.id = :contactId AND uc.isConfirmed = true")
    List<UserContact> findByContactIdAndIsConfirmedTrue(@Param("contactId") String contactId);

    void deleteByUserAndContact(User user, User contact);

    @Query("SELECT COUNT(uc) > 0 FROM UserContact uc WHERE uc.user.id = :userId AND uc.contact.id = :contactId AND uc.isConfirmed = true")
    boolean existsConfirmedContact(@Param("userId") String userId, @Param("contactId") String contactId);
}

