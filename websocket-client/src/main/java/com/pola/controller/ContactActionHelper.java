package com.pola.controller;

import com.pola.model.Contact;
import com.pola.service.ContactService;
import com.pola.view.ChatDialogs;

/**
 * Helper para manejar las acciones de confirmación y ejecución de operaciones sobre contactos.
 * Extraído de ChatController para mejorar la claridad y separación de responsabilidades.
 */
public class ContactActionHelper {
    private final ContactService contactService;
    private final ChatController chatController;

    public ContactActionHelper(ContactService contactService, ChatController chatController) {
        this.contactService = contactService;
        this.chatController = chatController;
    }

    public void handleAddContact() {
        ChatDialogs.showAddContactDialog(chatController.getCurrentUsername(), (username) -> {
            Contact contact = contactService.addContact(chatController.getCurrentUserId(), username, true);
            return contact != null;
        });
    }

    public void confirmAddContact(Contact contact) {
        ChatDialogs.showConfirmation(
            "Añadir Contacto", 
            null, 
            "¿Quieres añadir este usuario a tu lista de contactos?", 
            () -> {
                contactService.confirmContact(contact);
                chatController.refreshContactsList();
            }
        );
    }

    public void confirmDeleteContact(Contact contact) {
        ChatDialogs.showConfirmation(
            "Eliminar Contacto", 
            "¿Estás seguro de eliminar a " + contact.getContactUsername() + "?", 
            "Esta acción eliminará el contacto de tu lista local.", 
            () -> {
                contactService.deleteContact(contact);
                chatController.resetChatViewIfSelected(contact);
            }
        );
    }

    public void confirmBlockContact(Contact contact) {
        ChatDialogs.showConfirmation(
            "Bloquear Contacto", 
            "¿Bloquear a " + contact.getContactUsername() + "?", 
            "No podrás recibir mensajes de este usuario.", 
            () -> {
                contactService.blockContact(contact);
                chatController.resetChatViewIfSelected(contact);
            }
        );
    }

    public void confirmUnblockContact(Contact contact) {
        ChatDialogs.showConfirmation(
            "Desbloquear Contacto", 
            "¿Desbloquear a " + contact.getContactUsername() + "?", 
            "Podrás volver a intercambiar mensajes.", 
            () -> contactService.unblockContact(contact)
        );
    }
}