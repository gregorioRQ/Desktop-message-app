package com.pola.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pola.database.DatabaseManager;
import com.pola.model.ChatMessage;
import com.pola.model.ImageChatMessage;

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
 * - status: Estado del mensaje (PENDING, SENT, DELIVERED, READ, FAILED)
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
        return create(message, "text", false);
    }

    public ChatMessage create(ChatMessage message, String type, boolean downloaded) throws SQLException {
        return create(message, type, downloaded, message.getContent());
    }

    public ChatMessage create(ChatMessage message, String type, boolean downloaded, String content) throws SQLException {
        String sql = """
            INSERT INTO messages (id, contact_username, sender_username, content, sender_id, status, type, downloaded)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

        log.debug("Creando mensaje - contact: {}, senderUsername: {}, content: {}, type: {}, status: {}",
                message.getContactUsername(), message.getSenderUsername(), content, type, message.getStatus());

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, message.getId());
            stmt.setString(2, message.getContactUsername());
            stmt.setString(3, message.getSenderUsername());
            stmt.setString(4, content);
            stmt.setString(5, message.getSenderId());
            stmt.setString(6, message.getStatus().name());
            stmt.setString(7, type);
            stmt.setInt(8, downloaded ? 1 : 0);

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

            log.info("Mensaje creado con ID: {}, status: {}", message.getId(), message.getStatus());
            return message;
        }
    }

    /**
     * Obtiene los IDs de los mensajes por estado de un contacto.
     * @param contactUsername Nombre de usuario del contacto
     * @param statuses Estados a filtrar (ej. DELIVERED, SENT)
     * @return Lista de IDs de mensajes que coinciden con los estados
     */
    public List<Long> getMessageIdsByStatus(String contactUsername, ChatMessage.MessageStatus... statuses) throws SQLException {
        if (statuses == null || statuses.length == 0) {
            return new ArrayList<>();
        }

        String placeholders = String.join(",", Collections.nCopies(statuses.length, "?"));
        String sql = "SELECT id FROM messages WHERE contact_username = ? AND status IN (" + placeholders + ")";
        List<Long> ids = new ArrayList<>();

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, contactUsername);
            for (int i = 0; i < statuses.length; i++) {
                stmt.setString(i + 2, statuses[i].name());
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong("id"));
                }
            }
        }
        return ids;
    }

    /**
     * Actualiza el estado de un mensaje por ID.
     * @param messageId ID del mensaje
     * @param status Nuevo estado
     * @throws SQLException Si ocurre un error de base de datos
     */
    public void updateStatus(Long messageId, ChatMessage.MessageStatus status) throws SQLException {
        String sql = "UPDATE messages SET status = ? WHERE id = ?";

        log.debug("Actualizando estado del mensaje {} a {}", messageId, status);

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, status.name());
            stmt.setLong(2, messageId);
            stmt.executeUpdate();

            log.info("Estado del mensaje {} actualizado a {}", messageId, status);
        }
    }

    /**
     * Actualiza el estado de múltiples mensajes por ID (Batch update).
     * @param messageIds Lista de IDs de mensajes
     * @param status Nuevo estado
     * @throws SQLException Si ocurre un error de base de datos
     */
    public void updateMultipleStatus(List<Long> messageIds, ChatMessage.MessageStatus status) throws SQLException {
        if (messageIds == null || messageIds.isEmpty()) return;

        String sql = "UPDATE messages SET status = ? WHERE id = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            conn.setAutoCommit(false);

            for (Long id : messageIds) {
                stmt.setString(1, status.name());
                stmt.setLong(2, id);
                stmt.addBatch();
            }

            stmt.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);

            log.info("Actualizados {} mensajes a estado {}", messageIds.size(), status);
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
            SELECT id, contact_username, sender_username, content, sender_id, timestamp, status, type, downloaded
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
            SELECT id, contact_id, content, sender_id, timestamp, status
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
     * Marca todos los mensajes de un contacto como leídos
     */
    public void markAllAsReadByContactUsername(String username) throws SQLException {
        String sql = "UPDATE messages SET status = ? WHERE contact_username = ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, ChatMessage.MessageStatus.READ.name());
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
    }

    /**
     * Cuenta mensajes no leídos de un contacto (mensajes recibidos con status != READ)
     */
    public int countUnreadByContactUsername(String username) throws SQLException {
        String sql = """
            SELECT COUNT(*) as count
            FROM messages
            WHERE contact_username = ? AND status != ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username);
            stmt.setString(2, ChatMessage.MessageStatus.READ.name());

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
        String type = rs.getString("type");
        boolean downloaded = rs.getInt("downloaded") == 1;
        String statusStr = rs.getString("status");
        ChatMessage.MessageStatus status = ChatMessage.MessageStatus.fromString(statusStr);

        if ("image".equals(type)) {
            String content = rs.getString("content");
            String imageUrl = "";
            String mediaId = "";
            int width = 0;
            int height = 0;

            try {
                ObjectMapper mapper = new ObjectMapper();
                ImageJsonData jsonData = mapper.readValue(content, ImageJsonData.class);
                imageUrl = jsonData.imageUrl;
                mediaId = jsonData.mediaId;
                width = jsonData.width;
                height = jsonData.height;
            } catch (Exception e) {
                log.warn("Error al parsear content de imagen: {}", e.getMessage());
            }

            ImageChatMessage imageMessage = new ImageChatMessage(
                    rs.getString("contact_username"),
                    rs.getString("sender_id"),
                    imageUrl,
                    mediaId,
                    width,
                    height
            );
            imageMessage.setDownloaded(downloaded);
            imageMessage.setStatus(status);
            return imageMessage;
        }

        ChatMessage message = new ChatMessage(
                rs.getLong("id"),
                rs.getString("contact_username"),
                rs.getString("sender_username"),
                rs.getString("content"),
                rs.getString("sender_id"),
                rs.getTimestamp("timestamp").toLocalDateTime(),
                status != ChatMessage.MessageStatus.READ
        );
        message.setStatus(status);
        return message;
    }

    private static class ImageJsonData {
        public String imageUrl;
        public String mediaId;
        public int width;
        public int height;
    }
}
