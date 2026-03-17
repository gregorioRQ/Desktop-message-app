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

/**
 * Repositorio para gestionar mensajes en la base de datos local.
 * Proporciona operaciones CRUD para mensajes entre el usuario actual y sus contactos.
 * 
 * La tabla messages almacena:
 * - id: Identificador único del mensaje
 * - contact_username: Nombre de usuario del contacto (destinatario/remitente)
 * - sender_username: Nombre de usuario del remitente del mensaje
 * - content: Contenido del mensaje
 * - sender_id: ID del dispositivo/remitente
 * - timestamp: Fecha y hora del mensaje
 * - is_read: Indicador de mensaje leído
 */
public class MessageRepository {
    private final DatabaseManager dbManager;
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MessageRepository.class);

    public MessageRepository(){
        this.dbManager = DatabaseManager.getInstance();
    }

    /**
     * Crea un nuevo mensaje en la base de datos local.
     * @param message El mensaje a crear
     * @return El mensaje creado con su ID asignado
     * @throws SQLException Si ocurre un error de base de datos
     */
    public ChatMessage create(ChatMessage message) throws SQLException {
        String sql = """
            INSERT INTO messages (id, contact_username, sender_username, content, sender_id, is_read)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        
        log.debug("Creando mensaje - contact: {}, senderUsername: {}, content: {}", 
            message.getContactUsername(), message.getSenderUsername(), message.getContent());
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, message.getId());
            stmt.setString(2, message.getContactUsername());
            stmt.setString(3, message.getSenderUsername());
            stmt.setString(4, message.getContent());
            stmt.setString(5, message.getSenderId());
            stmt.setInt(6, message.isRead() ? 1 : 0);
            
            int affectedRows = stmt.executeUpdate();
            
            if (affectedRows == 0) {
                throw new SQLException("Fallo al crear mensaje");
            }
            
            String idQuery = "SELECT last_insert_rowid() as id";
            try (Statement idStatement = conn.createStatement();
                ResultSet rs = idStatement.executeQuery(idQuery)
                ) {
                if (rs.next()) {
                    message.setId(rs.getLong("id"));
                }
            }
            
            log.info("Mensaje creado con ID: {}", message.getId());
            return message;
        }
    }
    
    /**
     * Obtiene los IDs de los mensajes no leídos de un contacto
     */
    public List<Long> getUnreadMessageIds(String contactUsername) throws SQLException {
        String sql = "SELECT id FROM messages WHERE contact_username = ? AND is_read = 0";
        List<Long> ids = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, contactUsername);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                }
            }
        }
        return ids;
    }

    /**
     * Marca una lista de mensajes como leídos por ID (Batch update)
     */
    public void markMultipleAsRead(List<Long> messageIds) throws SQLException {
        if (messageIds == null || messageIds.isEmpty()) return;

        String sql = "UPDATE messages SET is_read = 1 WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            conn.setAutoCommit(false); // Iniciar transacción
            
            for (Long id : messageIds) {
                stmt.setLong(1, id);
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        }
    }

    /**
     * Obtiene todos los mensajes de un contacto específico.
     * @param username El nombre de usuario del contacto
     * @return Lista de mensajes ordenados cronológicamente
     * @throws SQLException Si ocurre un error de base de datos
     */
    public List<ChatMessage> findByContactUsername(String username) throws SQLException {
        String sql = """
            SELECT id, contact_username, sender_username, content, sender_id, timestamp, is_read
            FROM messages
            WHERE contact_username = ?
            ORDER BY timestamp ASC
            """;
        
        log.debug("Cargando historial de mensajes para contacto: {}", username);
        
        List<ChatMessage> messages = new ArrayList<>();
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(mapResultSetToMessage(rs));
                }
            }
        }
        
        log.info("Cargados {} mensajes para contacto: {}", messages.size(), username);
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
     * Verifica si un mensaje existe por ID
     */
    public boolean existsById(Long id) throws SQLException {
        String sql = "SELECT 1 FROM messages WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    /**
     * Marca un mensaje como leído
     */
    public void markAsRead(Long messageId) throws SQLException {
        String sql = "UPDATE messages SET is_read = 1 WHERE id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, messageId);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Marca todos los mensajes de un contacto como leídos
     */
    public void markAllAsReadByContactUsername(String username) throws SQLException {
        String sql = "UPDATE messages SET is_read = 1 WHERE contact_username = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            stmt.executeUpdate();
        }
    }
    
    /**
     * Cuenta mensajes no leídos de un contacto
     */
    public int countUnreadByContactUsername(String username) throws SQLException {
        String sql = """
            SELECT COUNT(*) as count
            FROM messages
            WHERE contact_username = ? AND is_read = 0
            """;
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, username);
            
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("count");
                }
            }
        }
        
        return 0;
    }
    /**
     * Elimina un mensaje específico por su ID.
     * @param messageId ID del mensaje a eliminar
     * @throws SQLException Si ocurre un error de base de datos
     */
    public void delete(Long messageId) throws SQLException {
        String sql = "DELETE FROM messages WHERE id = ?";
        
        log.debug("Eliminando mensaje con ID: {}", messageId);
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, messageId);
            int deleted = stmt.executeUpdate();
            if(deleted > 0){
                log.info("Mensaje eliminado - ID: {}", messageId);
            }
        }
    }

    public void updateContent(Long messageId, String newContent) throws SQLException {
        String sql = "UPDATE messages SET content = ? WHERE id = ?";
        
        log.debug("Editando mensaje ID: {} - Nuevo contenido: {}", messageId, newContent);
        
        try(Connection conn = dbManager.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sql)
    ){
        stmt.setString(1, newContent);
        stmt.setLong(2, messageId);
        stmt.executeUpdate();
        
        log.info("Mensaje actualizado - ID: {}", messageId);
    }
    }

    /**
     * Elimina todos los mensajes de un contacto específico de la DB local.
     * Este método elimina tanto los mensajes enviados por el usuario actual
     * como los recibidos del contacto.
     * 
     * @param contactUsername El nombre de usuario del contacto cuyo historial se eliminará
     * @throws SQLException Si ocurre un error de base de datos
     */
    public void deleteByContactUsername(String contactUsername) throws SQLException {
        String sql = "DELETE FROM messages WHERE contact_username = ?";
        
        log.info("Eliminando historial de mensajes con contacto: {}", contactUsername);
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, contactUsername);
            int deletedCount = stmt.executeUpdate();
            
            log.info("Historial eliminado para contacto: {} - Mensajes borrados: {}", 
                contactUsername, deletedCount);
        }
    }
    
    /**
     * Mapea un ResultSet a un objeto ChatMessage.
     * @param rs El ResultSet con los datos del mensaje
     * @return Objeto ChatMessage con los datos mapeados
     * @throws SQLException Si ocurre un error al leer el ResultSet
     */
    private ChatMessage mapResultSetToMessage(ResultSet rs) throws SQLException {
        ChatMessage message = new ChatMessage(
            rs.getLong("id"),
            rs.getString("contact_username"),
            rs.getString("sender_username"),
            rs.getString("content"),
            rs.getString("sender_id"),
            rs.getTimestamp("timestamp").toLocalDateTime(),
            rs.getInt("is_read") == 1
        );
        return message;
    }
}
