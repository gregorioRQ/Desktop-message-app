package com.basic_chat.notifiation_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.basic_chat.notifiation_service.model.User;
import com.basic_chat.notifiation_service.model.UserContact;

public interface UserContactRepository extends JpaRepository<UserContact, Long> {
    List<UserContact> findByUser(User user);
}

