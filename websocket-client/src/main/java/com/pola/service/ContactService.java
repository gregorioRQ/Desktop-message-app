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
import com.pola.repository.MessageRepository;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ContactService {

    private final ContactRepository contactRepository;
    private final BlockedUserRepository blockedUserRepository;
    private final MessageRepository messageRepository;
    private final ObservableList<Contact> contacts;
    private final ObservableList<Contact> blockedContacts;
    private WebSocketService webSocketService;
    private MessageSender messageSender;
    private String currentUserId;
    private String currentUsername;
    
    private Set<String> usersWhoBlockedMe;
    private Set<String> onlineUsers;
    private Consumer<String> onBlockedByListener;
    private Consumer<String> onUnblockedByListener;
    private Runnable onOnlineStatusChanged;

    public ContactService(){
        this.contactRepository = new ContactRepository();
        this.blockedUserRepository = new BlockedUserRepository();
        this.messageRepository = new MessageRepository();
        this.contacts = FXCollections.observableArrayList();
        this.blockedContacts = FXCollections.observableArrayList();
        this.usersWhoBlockedMe = blockedUserRepository.findAll();
        this.onlineUsers = new HashSet<>();
    }

    public void setWebSocketService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
        this.messageSender = new MessageSender(webSocketService);
    }

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    public void setCurrentUsername(String currentUsername) {
        this.currentUsername = currentUsername;
    }

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

    public Contact addContact(String userId, String contactUsername) {
        try {
            Optional<Contact> existing = contactRepository.findByUserIdAndContactUsername(userId, contactUsername);
            if (existing.isPresent()) {
                Contact c = existing.get();
                if (c.isBlocked()) {
                    unblockContact(c);
                    return c;
                }
                System.out.println("El contacto ya existe: " + contactUsername);
                return existing.get();
            }
            
            Contact contact = new Contact(userId, contactUsername);
            Contact created = contactRepository.create(contact);
            
            Platform.runLater(() -> contacts.add(created));
            
            System.out.println("Contacto agregado: " + contactUsername);
            return created;
            
        } catch (SQLException e) {
            System.err.println("Error agregando contacto: " + e.getMessage() + e.getLocalizedMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void sendAddContactRequest(String contactUsername) {
        if (currentUserId == null || currentUsername == null) {
            System.err.println("ERROR: currentUserId o currentUsername son null. No se puede enviar AddContactRequest.");
            return;
        }
        
        if (webSocketService != null && webSocketService.isConnected() && messageSender != null) {
            messageSender.sendAddContactRequest(currentUserId, currentUsername, contactUsername);
            System.out.println("AddContactRequest enviado para: " + contactUsername);
        } else {
            System.err.println("ERROR: WebSocket no conectado o messageSender null. No se puede enviar AddContactRequest.");
        }
    }

    public void deleteContact(Contact contact) {
        if (contact == null) return;
        
        String logPrefix = "[ContactService] ";
        System.out.println(logPrefix + "Iniciando proceso de eliminación para el contacto: " + contact.getContactUsername());

        try {
            int index = contacts.indexOf(contact);
            contactRepository.delete(contact.getId());
            Platform.runLater(() -> {
                if (index >= 0) {
                    contacts.remove(index);
                }
            });
            System.out.println(logPrefix + "Contacto eliminado exitosamente: " + contact.getContactUsername());
        } catch (SQLException e) {
            System.err.println(logPrefix + "Error al eliminar contacto: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void blockContact(Contact contact) {
        try {
            contact.setBlocked(true);
            contactRepository.update(contact);
            
            int index = contacts.indexOf(contact);
            if (index != -1) {
                final int finalIndex = index;
                Platform.runLater(() -> {
                    Contact removed = contacts.remove(finalIndex);
                    blockedContacts.add(removed);
                });
            }
            
            System.out.println("Contacto bloqueado: " + contact.getContactUsername());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void unblockContact(Contact contact) {
        try {
            contact.setBlocked(false);
            contactRepository.update(contact);
            
            int index = blockedContacts.indexOf(contact);
            if (index != -1) {
                final int finalIndex = index;
                Platform.runLater(() -> {
                    Contact removed = blockedContacts.remove(finalIndex);
                    contacts.add(removed);
                });
            }
            
            System.out.println("Contacto desbloqueado: " + contact.getContactUsername());
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Optional<Contact> findContactByUsername(String userId, String contactUsername) throws SQLException {
        return contactRepository.findByUserIdAndContactUsername(userId, contactUsername);
    }

    public Contact getContact(String userId, String contactUsername) {
        try {
            return findContactByUsername(userId, contactUsername).orElse(null);
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public ObservableList<Contact> getContacts() {
        return contacts;
    }

    public ObservableList<Contact> getBlockedContacts() {
        return blockedContacts;
    }

    public void markUserAsBlockingMe(String username) {
        if (!usersWhoBlockedMe.contains(username)) {
            usersWhoBlockedMe.add(username);
            blockedUserRepository.add(username);
            if (onBlockedByListener != null) {
                onBlockedByListener.accept(username);
            }
        }
    }

    public boolean isUserBlockingMe(String username) {
        return usersWhoBlockedMe.contains(username);
    }

    public void setContactOnlineByUsername(String contactUsername, boolean online) {
        contacts.stream()
            .filter(c -> contactUsername.equals(c.getContactUsername()))
            .findFirst()
            .ifPresent(c -> {
                boolean changed = c.isOnline() != online;
                c.setOnline(online);
                try {
                    contactRepository.update(c);
                } catch (SQLException e) {
                    System.err.println("Error actualizando estado online en DB: " + e.getMessage());
                }
                if (changed && onOnlineStatusChanged != null) {
                    onOnlineStatusChanged.run();
                }
            });
        blockedContacts.stream()
            .filter(c -> contactUsername.equals(c.getContactUsername()))
            .findFirst()
            .ifPresent(c -> {
                boolean changed = c.isOnline() != online;
                c.setOnline(online);
                try {
                    contactRepository.update(c);
                } catch (SQLException e) {
                    System.err.println("Error actualizando estado online en DB: " + e.getMessage());
                }
                if (changed && onOnlineStatusChanged != null) {
                    onOnlineStatusChanged.run();
                }
            });
    }

    public boolean isContactOnlineByUsername(String contactUsername) {
        return contacts.stream()
            .anyMatch(c -> contactUsername.equals(c.getContactUsername()) && c.isOnline());
    }

    public void clearOnlineUsers() {
        if (!onlineUsers.isEmpty()) {
            onlineUsers.clear();
            if (onOnlineStatusChanged != null) {
                onOnlineStatusChanged.run();
            }
        }
    }

    public void setOnBlockedByListener(Consumer<String> listener) {
        this.onBlockedByListener = listener;
    }

    public void markUserAsUnblockingMe(String username) {
        boolean removed = usersWhoBlockedMe.remove(username);
        blockedUserRepository.remove(username);
        
        if (onUnblockedByListener != null) {
            onUnblockedByListener.accept(username);
        }
        
        if (removed) {
            System.out.println("Usuario removido de la lista de bloqueos: " + username);
        }
    }

    public void setOnUnblockedByListener(Consumer<String> listener) {
        this.onUnblockedByListener = listener;
    }

    public void setOnOnlineStatusChangedListener(Runnable listener) {
        this.onOnlineStatusChanged = listener;
    }

    public String getCurrentUserId() {
        return currentUserId;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public void notifyContactWeAreOnline(String contactUserId) {
        // Ya no es necesario - el registro de contacto se crea cuando el otro usuario presiona "agregar"
    }

    public void onConnectionEstablished() {
        // Notificar a los contactos que estamos online - ya no se hace aquí
        // El notification-service maneja esto internamente
    }

    /**
     * Marca todos los contactos como offline.
     * 
     * Este método se llama cuando el usuario se conecta al SSE para
     * resetear el estado de todos los contactos antes de recibir
     * la lista actualizada de contactos online.
     */
    public void resetAllContactsOffline() {
        boolean changed = false;
        
        // Marcar contactos normales como offline
        for (Contact contact : contacts) {
            if (contact.isOnline()) {
                contact.setOnline(false);
                try {
                    contactRepository.update(contact);
                    changed = true;
                } catch (SQLException e) {
                    System.err.println("[ContactService] Error actualizando contacto a offline: " + e.getMessage());
                }
            }
        }
        
        // Marcar contactos bloqueados como offline
        for (Contact contact : blockedContacts) {
            if (contact.isOnline()) {
                contact.setOnline(false);
                try {
                    contactRepository.update(contact);
                    changed = true;
                } catch (SQLException e) {
                    System.err.println("[ContactService] Error actualizando contacto bloqueado a offline: " + e.getMessage());
                }
            }
        }
        
        // Limpiar la lista de usuarios online
        onlineUsers.clear();
        
        if (changed && onOnlineStatusChanged != null) {
            onOnlineStatusChanged.run();
        }
        
        System.out.println("[ContactService] Todos los contactos marcados como offline");
    }

    /**
     * Marca una lista de contactos como online.
     * 
     * Este método se llama cuando se recibe la lista de contactos online
     * desde el notification-service vía SSE.
     * 
     * @param usernames Lista de usernames de contactos que están online
     */
    public void setContactsOnline(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return;
        }
        
        boolean changed = false;
        
        for (String username : usernames) {
            // Buscar en contactos normales
            contacts.stream()
                .filter(c -> username.equals(c.getContactUsername()))
                .findFirst()
                .ifPresent(c -> {
                    if (!c.isOnline()) {
                        c.setOnline(true);
                        onlineUsers.add(username);
                        try {
                            contactRepository.update(c);
                        } catch (SQLException e) {
                            System.err.println("[ContactService] Error actualizando contacto a online: " + e.getMessage());
                        }
                        if (onOnlineStatusChanged != null) {
                            onOnlineStatusChanged.run();
                        }
                    }
                });
            
            // Buscar en contactos bloqueados
            blockedContacts.stream()
                .filter(c -> username.equals(c.getContactUsername()))
                .findFirst()
                .ifPresent(c -> {
                    if (!c.isOnline()) {
                        c.setOnline(true);
                        try {
                            contactRepository.update(c);
                        } catch (SQLException e) {
                            System.err.println("[ContactService] Error actualizando contacto bloqueado a online: " + e.getMessage());
                        }
                        if (onOnlineStatusChanged != null) {
                            onOnlineStatusChanged.run();
                        }
                    }
                });
        }
        
        System.out.println("[ContactService] " + usernames.size() + " contactos marcados como online");
    }
}