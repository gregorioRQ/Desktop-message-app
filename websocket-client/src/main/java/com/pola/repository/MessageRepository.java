package com.pola.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.pola.database.DatabaseManager;
import com.pola.model.ChatMessage;

// Repositorio para gestionar mensajes
public class MessageRepository {
    private final DatabaseManager dbManager;

    public MessageRepository(){
        this.dbManager = DatabaseManager.getInstance();
    }

    // Crear un mensaje
    public ChatMessage create(ChatMessage message) throws SQLException {
        String sql = """
            INSERT INTO messages (contact_id, content, sender_id, is_read)
            VALUES (?, ?, ?, ?)
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            stmt.setInt(1, message.getContactId());
            stmt.setString(2, message.getContent());
            stmt.setString(3, message.getSenderId());
            stmt.setInt(4, message.isRead() ? 1 : 0);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Fallo al crear mensaje");
            }
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    message.setId(generatedKeys.getInt(1));
                }
            }
            
            return message;
        }
    }
    
    /**
     * Obtiene todos los mensajes de un contacto
     */
    public List<ChatMessage> findByContactId(int contactId) throws SQLException {
        String sql = """
            SELECT id, contact_id, content, sender_id, timestamp, is_read
            FROM messages
            WHERE contact_id = ?
            ORDER BY timestamp ASC
            """;
        
        List<ChatMessage> messages = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, contactId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
            }
        }
        
        return messages;
    }
    
    /**
     * Obtiene los últimos N mensajes de un contacto
     */
    public List<ChatMessage> findLastNByContactId(int contactId, int limit) throws SQLException {
        String sql = """
            SELECT id, contact_id, content, sender_id, timestamp, is_read
            FROM messages
            WHERE contact_id = ?
            ORDER BY timestamp DESC
            LIMIT ?
            """;
        
        List<ChatMessage> messages = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, contactId);
            stmt.setInt(2, limit);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
            }
        }
        
        // Invertir para tener orden cronológico
        messages.sort((m1, m2) -> m1.getTimestamp().compareTo(m2.getTimestamp()));
        
        return messages;
    }
    
    /**
     * Marca un mensaje como leído
     */
    public void markAsRead(int messageId) throws SQLException {
        String sql = "UPDATE messages SET is_read = 1 WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, messageId);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Marca todos los mensajes de un contacto como leídos
     */
    public void markAllAsReadByContactId(int contactId) throws SQLException {
        String sql = "UPDATE messages SET is_read = 1 WHERE contact_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, contactId);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Cuenta mensajes no leídos de un contacto
     */
    public int countUnreadByContactId(int contactId) throws SQLException {
        String sql = """
            SELECT COUNT(*) as count
            FROM messages
            WHERE contact_id = ? AND is_read = 0
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, contactId);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }
        
        return 0;
    }
    
    /**
     * Elimina todos los mensajes de un contacto
     */
    public void deleteByContactId(int contactId) throws SQLException {
        String sql = "DELETE FROM messages WHERE contact_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setInt(1, contactId);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Mapea un ResultSet a un objeto ChatMessage
     */
    private ChatMessage mapResultSetToMessage(ResultSet rs) throws SQLException {
        return new ChatMessage(
            rs.getInt("id"),
            rs.getInt("contact_id"),
            rs.getString("content"),
            rs.getString("sender_id"),
            rs.getTimestamp("timestamp").toLocalDateTime(),
            rs.getInt("is_read") == 1
        );
    }
}
