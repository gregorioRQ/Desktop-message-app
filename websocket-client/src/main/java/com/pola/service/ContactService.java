package com.pola.service;

import java.sql.SQLException;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;

import com.pola.model.Contact;
import com.pola.repository.BlockedUserRepository;
import com.pola.repository.ContactRepository;
import com.pola.proto.MessagesProto;
import com.pola.proto.MessagesProto.WsMessage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ContactService {

    private final ContactRepository contactRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final ObservableList<Contact> contacts;
    private final ObservableList<Contact> blockedContacts;
    private WebSocketService webSocketService;
    private NotificationService notificationService;
    private String currentUserId;
    private String currentUsername;
    
    // Cache en memoria de usuarios que me han bloqueado
    private Set<String> usersWhoBlockedMe;
    private Consumer<String> onBlockedByListener;
    private Consumer<String> onUnblockedByListener;

    public ContactService(){
        this.contactRepository = new ContactRepository();
        this.blockedUserRepository = new BlockedUserRepository();
        this.contacts = FXCollections.observableArrayList();
        this.blockedContacts = FXCollections.observableArrayList();
        this.usersWhoBlockedMe = blockedUserRepository.findAll();
    }

    public void setWebSocketService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public void setCurrentUsername(String currentUsername) {
        this.currentUsername = currentUsername;
    }

    // Carga los contactos de un usuario desde la db
    public void loadContacts(String userId){
        try{
            List<Contact> allContacts = contactRepository.findByUserId(userId);
            contacts.setAll(allContacts.stream().filter(c -> !c.isBlocked()).collect(Collectors.toList()));
            blockedContacts.setAll(allContacts.stream().filter(c -> c.isBlocked()).collect(Collectors.toList()));
            System.out.println("Contactos cargados: "+ contacts.size());
        }catch (SQLException ex){
            ex.printStackTrace();
        }
        
    }

    /**
     * Agrega un nuevo contacto
     */
    public Contact addContact(String userId, String contactUsername) {
        try {
            // Verificar si ya existe
            Optional<Contact> existing = contactRepository.findByUserIdAndContactUsername(userId, contactUsername);
            if (existing.isPresent()) {
                Contact c = existing.get();
                if (c.isBlocked()) {
                    // Si está bloqueado, lo desbloqueamos al intentar agregarlo de nuevo
                    unblockContact(c);
                    return c;
                }
                System.out.println("El contacto ya existe: " + contactUsername);
                return existing.get();
            }
            
            // Crear nuevo contacto
            Contact contact = new Contact(userId, contactUsername);
            Contact created = contactRepository.create(contact);
            
            // Agregar a la lista observable
            contacts.add(created);
            
            // Enviar notificación STOMP de contacto agregado
            if (notificationService != null) {
                notificationService.sendAddContactNotification(currentUsername, contactUsername);
            }
            
            System.out.println("Contacto agregado: " + contactUsername);
            return created;
            
        } catch (SQLException e) {
            System.err.println("Error agregando contacto: " + e.getMessage() + e.getLocalizedMessage());
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
            blockedContacts.add(contact);
            
            // Enviar petición de bloqueo al servidor
            if (webSocketService != null && webSocketService.isConnected()) {
                MessagesProto.BlockContactRequest request = MessagesProto.BlockContactRequest.newBuilder()
                    .setRecipient(contact.getContactUsername())
                    .build();
                
                WsMessage msg = WsMessage.newBuilder().setBlockContactRequest(request).build();
                webSocketService.sendMessage(msg);
            }
            
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
            blockedContacts.remove(contact);
            contacts.add(contact); // Agregar a la lista visible
            
            // Enviar petición de desbloqueo al servidor
            if (webSocketService != null && webSocketService.isConnected()) {
                MessagesProto.UnblockContactRequest request = MessagesProto.UnblockContactRequest.newBuilder()
                    .setRecipient(contact.getContactUsername())
                    .build();
                
                WsMessage msg = WsMessage.newBuilder().setUnblockContactRequest(request).build();
                webSocketService.sendMessage(msg);
            }
            System.out.println("Contacto desbloqueado: " + contact.getContactUsername());
        } catch (SQLException e) {
            System.err.println("Error desbloqueando contacto: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Busca un contacto por contactUsername
     */
    public Optional<Contact> findContactByUsername(String userId, String contactUsername) {
        try {
            return contactRepository.findByUserIdAndContactUsername(userId, contactUsername);
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

    public ObservableList<Contact> getBlockedContacts() {
        return blockedContacts;
    }

    // Registra que un usuario específico ha bloqueado al cliente actual
    public void markUserAsBlockingMe(String username) {
        if (!usersWhoBlockedMe.contains(username)) {
            usersWhoBlockedMe.add(username);
            blockedUserRepository.add(username); // Persistir en DB
            if (onBlockedByListener != null) {
                onBlockedByListener.accept(username);
            }
        }
    }

    // Verifica si un usuario tiene bloqueado al cliente actual
    public boolean isUserBlockingMe(String username) {
        return usersWhoBlockedMe.contains(username);
    }

    // Listener para notificar a la UI cuando se detecta un bloqueo nuevo
    public void setOnBlockedByListener(Consumer<String> listener) {
        this.onBlockedByListener = listener;
    }

    // Registra que un usuario ha desbloqueado al cliente actual
    public void markUserAsUnblockingMe(String username) {
        // Intentar remover siempre para asegurar consistencia
        boolean removed = usersWhoBlockedMe.remove(username);
        blockedUserRepository.remove(username); // Eliminar de la DB
        
        // Notificar siempre a la UI para asegurar que el input se habilite
        if (onUnblockedByListener != null) {
            onUnblockedByListener.accept(username);
        }
        
        if (removed) {
            System.out.println("Usuario removido de la lista de bloqueos: " + username);
        }
    }

    // Listener para notificar a la UI cuando alguien nos desbloquea
    public void setOnUnblockedByListener(Consumer<String> listener) {
        this.onUnblockedByListener = listener;
    }
}
