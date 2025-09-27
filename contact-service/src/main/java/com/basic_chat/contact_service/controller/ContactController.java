package com.basic_chat.contact_service.controller;

import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.basic_chat.contact_service.model.Contact;
import com.basic_chat.contact_service.model.ContactAddedEvent;
import com.basic_chat.contact_service.model.ContactBlocked;
import com.basic_chat.contact_service.service.ContactService;
import com.basic_chat.contact_service.service.UserClient;

@RestController
@RequestMapping("/contacts")
public class ContactController {
    private final ContactService contactService;
    private final UserClient userClient;
    private final RabbitTemplate rabbitTemplate;

    public ContactController(ContactService contactService, UserClient userClient, RabbitTemplate rabbitTemplate) {
        this.contactService = contactService;
        this.userClient = userClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping
    public Contact addContact(@RequestParam Long userId, @RequestParam Long contactId) {
        // consulata con auth service para saber si el contacto a agregar existe
        if (!userClient.getUserById(contactId)) {
            System.out.println("El contacto no existe");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El contacto no existe");
        }
        rabbitTemplate.convertAndSend("contact.events", new ContactAddedEvent(userId, contactId));

        return contactService.addContact(userId, contactId);
    }

    @GetMapping("/{userId}")
    public List<Contact> getContacts(@RequestParam Long userId) {

        return contactService.getAllContacts(userId);
    }

    @GetMapping("/search-username")
    public ResponseEntity<Contact> searchContactsByUsername(@RequestParam String username) {
        Contact contact = contactService.searchContactsByUsername(username);
        if (contact != null) {
            return ResponseEntity.ok(contact);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/block-contact")
    public void blockContact(@RequestParam Long userId, @RequestParam Long blockedUserId) {
        contactService.blockContact(userId, blockedUserId);
    }

    @PostMapping("/unblock-contact")
    public void unblockContact(@RequestParam Long userId, @RequestParam Long blockedUserId) {
        contactService.unblockContact(userId, blockedUserId);
    }

    @GetMapping("/blocked-contacts")
    public List<ContactBlocked> getBlockedContacts(@RequestParam Long userId) {
        return contactService.getBlockedContacts(userId);
    }

    @GetMapping("/is-blocked")
    public boolean isUserBlocked(@RequestParam Long userId, @RequestParam Long contactId) {
        return contactService.isUserBlocked(userId, contactId);
    }

}
