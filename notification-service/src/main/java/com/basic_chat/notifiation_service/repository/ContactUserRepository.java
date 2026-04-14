package com.basic_chat.notifiation_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.basic_chat.notifiation_service.model.ContactUser;

@Repository
public interface ContactUserRepository extends JpaRepository<ContactUser, String> {

    List<ContactUser> findByUserId(String userId);

    List<ContactUser> findByContactUsername(String contactUsername);

    boolean existsByUserIdAndContactUsername(String userId, String contactUsername);
}