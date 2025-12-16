package com.pola.repository;


import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.pola.database.DatabaseManager;
import com.pola.model.Contact;

// Repositorio para la gestion de contactos
public class ContactRepository {
    private final DatabaseManager dbManager;

    public ContactRepository(){
        this.dbManager = DatabaseManager.getInstance();
    }

    // Crear un nuevo contacto
    public Contact create(Contact contact) throws SQLException{
        String sql = """
                INSERT INTO contacts (user_id, contact_user_id, contact_username, is_blocked) VALUES (?, ?, ?, ?)
                """;

        try(Connection conn = dbManager.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)){
                stmt.setString(1, contact.getUserId());
                stmt.setString(2, contact.getContactUserId());
                stmt.setString(3, contact.getContactUsername());
                stmt.setInt(4, contact.isBlocked() ? 1 : 0);

                int affectedRows = stmt.executeUpdate();

                if (affectedRows == 0){
                    throw new SQLException("Fallo al crear el contacto");
                }

                try(ResultSet generatedKeys = stmt.getGeneratedKeys()){
                    if(generatedKeys.next()){
                        contact.setId(generatedKeys.getInt(1));
                    }
                }
                System.out.println("Contacto creado");
                return contact;

            }
    }

    // Obtener todos los contactos de un usuario
    public List<Contact> findByUserId(String userId) throws SQLException{
        String sql = """
                SELECT id, user_id, contact_user_id, contact_username,
                is_blocked, created_at, updated_at FROM contacts WHERE user_id = ?
                AND is_blocked = 0 ORDER BY contact_username ASC
                """;
        List<Contact> contacts = new ArrayList<>();
        try(Connection conn = dbManager.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)){
                stmt.setString(1, userId);

                try(ResultSet rs = stmt.executeQuery()){
                    while (rs.next()){
                        contacts.add(mapResultSetToContact(rs));
                    }
                }
            }
        return contacts;   
    }

    /**
     * Busca un contacto específico por ID
     */
    public Optional<Contact> findById(int id) throws SQLException {
        String sql = """
            SELECT id, user_id, contact_user_id, contact_username, 
                   is_blocked, created_at, updated_at
            FROM contacts
            WHERE id = ?
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToContact(rs));
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Busca un contacto por userId y contactUserId
     */
    public Optional<Contact> findByUserIdAndContactUserId(String userId, String contactUserId) 
            throws SQLException {
        String sql = """
            SELECT id, user_id, contact_user_id, contact_username, 
                   is_blocked, created_at, updated_at
            FROM contacts
            WHERE user_id = ? AND contact_user_id = ?
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, userId);
            stmt.setString(2, contactUserId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToContact(rs));
                }
            }
        }
        
        return Optional.empty();
    }
    
    /**
     * Actualiza un contacto
     */
    public void update(Contact contact) throws SQLException {
        String sql = """
            UPDATE contacts
            SET contact_username = ?, is_blocked = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, contact.getContactUsername());
            stmt.setInt(2, contact.isBlocked() ? 1 : 0);
            stmt.setInt(3, contact.getId());
            
            stmt.executeUpdate();
            System.out.println("Contacto actualizado: " + contact.getContactUsername());
        }
    }
    
    /**
     * Elimina un contacto
     */
    public void delete(int id) throws SQLException {
        String sql = "DELETE FROM contacts WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, id);
            stmt.executeUpdate();
            System.out.println("Contacto eliminado: " + id);
        }
    }
    
    /**
     * Mapea un ResultSet a un objeto Contact
     */
    private Contact mapResultSetToContact(ResultSet rs) throws SQLException {
        return new Contact(
            rs.getInt("id"),
            rs.getString("user_id"),
            rs.getString("contact_user_id"),
            rs.getString("contact_username"),
            rs.getInt("is_blocked") == 1,
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }

}
