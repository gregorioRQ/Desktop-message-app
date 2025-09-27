package com.basic_chat.contact_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.basic_chat.contact_service.model.Contact;

public interface ContactRepository extends JpaRepository<Contact, Long> {
    List<Contact> findByUserId(Long userId);

    Contact findByUsername(String username);
}
