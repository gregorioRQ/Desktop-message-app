package com.basic_chat.contact_service.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.basic_chat.contact_service.model.Contact;
import com.basic_chat.contact_service.model.ContactBlocked;
import com.basic_chat.contact_service.repository.ContactBlockedRepository;
import com.basic_chat.contact_service.repository.ContactRepository;

@Service
public class ContactService {
    private final ContactRepository contactRepository;
    private final ContactBlockedRepository contactBlockedRepository;

    public ContactService(ContactRepository contactRepository, ContactBlockedRepository contactBlockedRepository) {
        this.contactRepository = contactRepository;
        this.contactBlockedRepository = contactBlockedRepository;
    }

    public Contact addContact(Long userId, Long contactId) {
        Contact contact = new Contact();
        contact.setUserId(userId);
        contact.setContact_id(contactId);
        return contactRepository.save(contact);
    }

    public List<Contact> getAllContacts(Long userId) {
        return contactRepository.findByUserId(userId);
    }

    public void blockContact(Long userId, Long blockedUserId) {
        if (isUserBlocked(userId, blockedUserId)) {
            throw new IllegalArgumentException("El usuario ya está bloqueado.");
        }
        ContactBlocked contactBlocked = new ContactBlocked();
        contactBlocked.setUserId(userId);
        contactBlocked.setBlockedUserId(blockedUserId);
        contactBlocked.setBlockedAt(LocalDateTime.now());
        contactBlockedRepository.save(contactBlocked);
    }

    public void unblockContact(Long userId, Long blockedUserId) {
        ContactBlocked contactBlocked = contactBlockedRepository.findByUserIdAndBlockedUserId(userId, blockedUserId)
                .orElseThrow(() -> new IllegalArgumentException("El usuario no está bloqueado."));
        contactBlockedRepository.delete(contactBlocked);
    }

    public boolean isUserBlocked(Long userId, Long blockedUserId) {
        return contactBlockedRepository.existsByUserIdAndBlockedUserId(userId, blockedUserId);
    }

    public List<ContactBlocked> getBlockedContacts(Long userId) {
        return contactBlockedRepository.findAllByUserId(userId);
    }

    public Contact searchContactsByUsername(String username) {
        return contactRepository.findByUsername(username);
    }

}
