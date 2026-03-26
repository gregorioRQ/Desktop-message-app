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
import com.pola.proto.MessagesProto;
import com.pola.proto.MessagesProto.WsMessage;

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
    // private NotificationService notificationService;
    private MessageSender messageSender;
    private String currentUserId;
    private String currentUsername;
    
    // Cache en memoria de usuarios que me han bloqueado
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

    /**
     * Establece el servicio de notificaciones STOMP.
     * @deprecated El servicio STOMP fue comentado. Ahora las notificaciones son vía SSE.
     */
    // public void setNotificationService(NotificationService notificationService) {
    //     this.notificationService = notificationService;
    // }

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
    public Contact addContact(String userId, String contactUsername, boolean isConfirmed) {
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
            Contact contact = new Contact(userId, contactUsername, null);
            contact.setConfirmed(isConfirmed);
            Contact created = contactRepository.create(contact);
            
            // Agregar a la lista observable
            Platform.runLater(() -> contacts.add(created));
            
            
            System.out.println("Contacto agregado: " + contactUsername);
            return created;
            
        } catch (SQLException e) {
            System.err.println("Error agregando contacto: " + e.getMessage() + e.getLocalizedMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Confirma un contacto y envía el ID real al servidor/usuario
     */
    public void confirmContact(Contact contact) {
        if (webSocketService != null && webSocketService.isConnected()) {
            System.out.println("Enviando identidad a: " + contact.getContactUsername());
            messageSender.sendContactIdentity(currentUserId, currentUsername, contact.getContactUsername());
        }
        
        try {
            contact.setConfirmed(true);
            contactRepository.update(contact);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Elimina un contacto de la lista local y prepara la petición al servidor.
     * Aplica Clean Code y logs detallados del proceso.
     */
    public void deleteContact(Contact contact) {
        if (contact == null) return;
        
        String logPrefix = "[ContactService] ";
        System.out.println(logPrefix + "Iniciando proceso de eliminación para el contacto: " + contact.getContactUsername());

        try {
            // 1. Eliminar de la persistencia local
            contactRepository.delete(contact.getId());
            
            // Eliminar mensajes locales asociados al contacto para mantener consistencia
            messageRepository.deleteByContactUsername(contact.getContactUsername());
            
            // 2. Actualizar estado en memoria (UI)
            Platform.runLater(() -> {
                contacts.remove(contact);
                blockedContacts.remove(contact); // Asegurar eliminación si estaba bloqueado
            });

            // 3. Enviar solicitud de eliminación al servidor de notificaciones
            // El servicio STOMP fue comentado. Ahora las notificaciones son vía SSE.
            // if (notificationService != null && contact.getContactUserId() != null) {
            //     notificationService.sendDropContactNotification(currentUserId, java.util.Collections.singletonList(contact.getContactUserId()));
            // }

            System.out.println(logPrefix + "Contacto eliminado exitosamente: " + contact.getContactUsername());
        } catch (SQLException e) {
            System.err.println(logPrefix + "Error crítico al eliminar contacto: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Bloquea un contacto
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
                    .setBlocker(currentUsername)      // Usuario que envía el bloqueo
                    .setRecipient(contact.getContactUsername()) // Usuario que será bloqueado
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
     * Desbloquea un contacto 
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
                    .setBlocker(currentUsername)      // Usuario que envía el desbloqueo
                    .setRecipient(contact.getContactUsername()) // Usuario que será desbloqueado
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
     * Actualiza el ID de un contacto existente
     */
    public void updateContactId(String contactUsername, String contactUserId) {
        // Buscar en la lista en memoria para actualizar el objeto observado por la UI
        Optional<Contact> listContact = contacts.stream()
            .filter(c -> c.getContactUsername().equals(contactUsername))
            .findFirst();
            
        // Si no está en la lista, buscar en DB (fallback)
        Contact contactToUpdate = listContact.orElse(
            findContactByUsername(currentUserId, contactUsername).orElse(null)
        );

        if (contactToUpdate != null) {
            try {
                contactToUpdate.setContactUserId(contactUserId);
                contactToUpdate.setConfirmed(true);
                contactRepository.update(contactToUpdate);
                
                // Forzar actualización en la lista observable si el contacto estaba en ella
                if (listContact.isPresent()) {
                    int index = contacts.indexOf(contactToUpdate);
                    if (index != -1) {
                        Platform.runLater(() -> contacts.set(index, contactToUpdate));
                    }
                }

                // Enviar notificación STOMP de contacto agregado cuando acepatamos al remitente.
                // El servicio STOMP fue comentado. Ahora las notificaciones son vía SSE.
                // if (notificationService != null) {
                //     notificationService.sendAddContactNotification(currentUserId, contactUserId);
                // }
            
                System.out.println("ID de contacto actualizado para: " + contactUsername);
            } catch (SQLException e) {
                System.err.println("Error actualizando ID de contacto: " + e.getMessage());
                e.printStackTrace();
            }
        }else{
            System.out.println("Contacto no encontrado contactUsername: " + contactUsername + " contactUserId: "+contactUserId);
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

    public void setContactOnline(String contactUserId, boolean online) {
        boolean changed = false;
        if (online) {
            changed = onlineUsers.add(contactUserId);
        } else {
            changed = onlineUsers.remove(contactUserId);
        }
        
        if (changed && onOnlineStatusChanged != null) {
            onOnlineStatusChanged.run();
        }
    }

    public boolean isContactOnline(String contactUserId) {
        return onlineUsers.contains(contactUserId);
    }

    public void clearOnlineUsers() {
        if (!onlineUsers.isEmpty()) {
            onlineUsers.clear();
            if (onOnlineStatusChanged != null) {
                onOnlineStatusChanged.run();
            }
        }
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

    public void setOnOnlineStatusChanged(Runnable listener) {
        this.onOnlineStatusChanged = listener;
    }

    /**
     * Notifica a un contacto que estamos en línea enviando nuestra identidad.
     * Esto permite que el usuario que acaba de conectarse sepa que nosotros ya estábamos aquí.
     */
    public void notifyContactWeAreOnline(String contactUserId) {
        if (contactUserId == null) return;

        contacts.stream()
            .filter(c -> contactUserId.equals(c.getContactUserId()))
            .findFirst()
            .ifPresent(contact -> {
                if (webSocketService != null && webSocketService.isConnected()) {
                    messageSender.sendContactIdentity(currentUserId, currentUsername, contact.getContactUsername());
                }
            });
    }
}
