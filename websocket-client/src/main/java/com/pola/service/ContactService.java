package com.pola.service;

import java.sql.SQLException;
import java.util.Optional;

import com.pola.model.Contact;
import com.pola.repository.ContactRepository;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ContactService {

    private final ContactRepository contactRepository;
    private final ObservableList<Contact> contacts;

    public ContactService(){
        this.contactRepository = new ContactRepository();
        this.contacts = FXCollections.observableArrayList();
    }

    // Carga los contactos de un usuario desde la db
    public void loadContacts(String userId){
        try{
            contacts.setAll(contactRepository.findByUserId(userId));
            System.out.println("Contactos cargados: "+ contacts.size());
        }catch (SQLException ex){
            ex.printStackTrace();
        }
        
    }

    /**
     * Agrega un nuevo contacto
     */
    public Contact addContact(String userId, String contactUserId, String contactUsername) {
        try {
            // Verificar si ya existe
            Optional<Contact> existing = contactRepository.findByUserIdAndContactUserId(userId, contactUserId);
            if (existing.isPresent()) {
                System.out.println("El contacto ya existe: " + contactUsername);
                return existing.get();
            }
            
            // Crear nuevo contacto
            Contact contact = new Contact(userId, contactUserId, contactUsername);
            Contact created = contactRepository.create(contact);
            
            // Agregar a la lista observable
            contacts.add(created);
            
            System.out.println("Contacto agregado: " + contactUsername);
            return created;
            
        } catch (SQLException e) {
            System.err.println("Error agregando contacto: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Elimina un contacto
     */
    public void removeContact(Contact contact) {
        try {
            contactRepository.delete(contact.getId());
            contacts.remove(contact);
            System.out.println("Contacto eliminado: " + contact.getContactUsername());
        } catch (SQLException e) {
            System.err.println("Error eliminando contacto: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Bloquea un contacto (funcionalidad futura)
     */
    public void blockContact(Contact contact) {
        try {
            contact.setBlocked(true);
            contactRepository.update(contact);
            contacts.remove(contact); // Remover de la lista visible
            System.out.println("Contacto bloqueado: " + contact.getContactUsername());
        } catch (SQLException e) {
            System.err.println("Error bloqueando contacto: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Desbloquea un contacto (funcionalidad futura)
     */
    public void unblockContact(Contact contact) {
        try {
            contact.setBlocked(false);
            contactRepository.update(contact);
            contacts.add(contact); // Agregar a la lista visible
            System.out.println("Contacto desbloqueado: " + contact.getContactUsername());
        } catch (SQLException e) {
            System.err.println("Error desbloqueando contacto: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Busca un contacto por contactUserId
     */
    public Optional<Contact> findContactByUserId(String userId, String contactUserId) {
        try {
            return contactRepository.findByUserIdAndContactUserId(userId, contactUserId);
        } catch (SQLException e) {
            System.err.println("Error buscando contacto: " + e.getMessage());
            e.printStackTrace();
            return Optional.empty();
        }
    }
    
    /**
     * Obtiene la lista observable de contactos
     */
    public ObservableList<Contact> getContacts() {
        return contacts;
    }
}
