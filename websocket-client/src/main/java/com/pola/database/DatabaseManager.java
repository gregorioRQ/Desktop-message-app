package com.pola.database;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    // Gestor de base de datos

    private String dbUrl;
    private static DatabaseManager instance;
    private Connection connection;
    private String currentUserId;

    private DatabaseManager(){
        // No inicializar la DB aquí - esperar a initializeForUser()
        this.dbUrl = null;
        this.currentUserId = null;
    }

    public static synchronized DatabaseManager getInstance(){
        if(instance == null){
            instance = new DatabaseManager();
        }
        return instance;
    }

    public void initializeForUser(String userId) {
        if (dbUrl != null && userId.equals(currentUserId)) {
            // Ya inicializada para este usuario
            System.out.println("BD ya inicializada para usuario: " + userId);
            return;
        }
        
        if (dbUrl != null) {
            // Cambio de usuario, cerrar conexión anterior
            System.out.println("Cambiando de usuario, cerrando BD anterior...");
            close();
        }
        
        this.currentUserId = userId;
        
        // Obtener directorio de datos
        String userHome = System.getProperty("user.home");
        String appDataDir = userHome + File.separator + ".chat-client" + 
                           File.separator + "users";
        
        // Crear directorios si no existen
        File dir = new File(appDataDir);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                System.out.println("Directorio creado: " + appDataDir);
            }
        }
        
        // Construir ruta de la BD específica para este usuario
        String dbFileName = "chat_client_" + sanitizeUserId(userId) + ".db";
        String dbPath = appDataDir + File.separator + dbFileName;
        this.dbUrl = "jdbc:sqlite:" + dbPath;
        
        System.out.println("Base de datos para usuario '" + userId + "':");
        System.out.println("   " + dbPath);
        
        // Crear tablas
        initializeDatabase();
    }

    private String sanitizeUserId(String userId){
        return userId.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
    }

    // Obtiene la conexión de la db
    public Connection getConnection() throws SQLException {
        if(dbUrl == null){
            throw new IllegalArgumentException("Base de datos no inicializada");
        }
        if(connection == null || connection.isClosed()){
            connection = DriverManager.getConnection(dbUrl);
        }
        return connection;
    }

    // Inicializar la db y crear las tablas
    private void initializeDatabase(){
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Tabla de contactos
            String createContactsTable = """
                CREATE TABLE IF NOT EXISTS contacts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id TEXT NOT NULL,
                    contact_username TEXT NOT NULL,
                contact_user_id TEXT,
                    is_blocked INTEGER DEFAULT 0,
                    is_confirmed INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(user_id, contact_username)
                )
                """;
            
            // Tabla de mensajes
            String createMessagesTable = """
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY,
                    contact_username TEXT NOT NULL,
                    sender_username TEXT NOT NULL,
                    content TEXT NOT NULL,
                    sender_id TEXT NOT NULL,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    is_read INTEGER DEFAULT 0,
                    FOREIGN KEY (contact_username) REFERENCES contacts(id) ON DELETE CASCADE
                )
                """;
            
            // Migración: agregar columna sender_username si no existe (para DBs existentes)
            String migrateSenderUsername = """
                ALTER TABLE messages ADD COLUMN sender_username TEXT NOT NULL DEFAULT ''
                """;
            
            String migrateType = """
                ALTER TABLE messages ADD COLUMN type TEXT DEFAULT 'text'
                """;
            
            String migrateDownloaded = """
                ALTER TABLE messages ADD COLUMN downloaded INTEGER DEFAULT 0
                """;
            
            // Índices para mejorar rendimiento
            String createContactsIndex = """
                CREATE INDEX IF NOT EXISTS idx_contacts_user_id 
                ON contacts(user_id)
                """;
            
            String createMessagesIndex = """
                CREATE INDEX IF NOT EXISTS idx_messages_contact_username
                ON messages(contact_username, timestamp DESC)
                """;
            
            String createMessagesSenderIndex = """
                CREATE INDEX IF NOT EXISTS idx_messages_sender_contact
                ON messages(sender_username, contact_username)
                """;
            
            // Tabla de usuarios que me han bloqueado
            String createBlockedByUsersTable = """
                CREATE TABLE IF NOT EXISTS blocked_by_users (
                    username TEXT PRIMARY KEY
                )
                """;
            
            // Tabla de imágenes descargadas
            String createImagesTable = """
                CREATE TABLE IF NOT EXISTS images (
                    media_id TEXT PRIMARY KEY,
                    message_id INTEGER,
                    image_data BLOB,
                    mime_type TEXT,
                    width INTEGER,
                    height INTEGER,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """;
            
            String createImagesMessageIdIndex = """
                CREATE INDEX IF NOT EXISTS idx_images_message_id
                ON images(message_id)
                """;
            
            stmt.execute(createContactsTable);
            stmt.execute(createMessagesTable);
            stmt.execute(createBlockedByUsersTable);
            stmt.execute(createImagesTable);
            stmt.execute(createImagesMessageIdIndex);
            stmt.execute(createContactsIndex);
            stmt.execute(createMessagesIndex);
            stmt.execute(createMessagesSenderIndex);
            
            // Ejecutar migración si es necesario
            try {
                stmt.execute(migrateSenderUsername);
                System.out.println("Migración sender_username aplicada");
            } catch (SQLException e) {
                System.out.println("Columna sender_username ya existe o índice ya creado");
            }
            
            try {
                stmt.execute(migrateType);
                System.out.println("Migración type aplicada");
            } catch (SQLException e) {
                System.out.println("Columna type ya existe");
            }
            
            try {
                stmt.execute(migrateDownloaded);
                System.out.println("Migración downloaded aplicada");
            } catch (SQLException e) {
                System.out.println("Columna downloaded ya existe");
            }
            
            System.out.println("Base de datos inicializada correctamente");
            
        } catch (SQLException e) {
            System.err.println("Error inicializando base de datos: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getCurrentUserId(){
        return currentUserId;
    }

    public String getDatabasePath(){
        if(dbUrl == null){
            return null;
        }
        return dbUrl.replace("jdbc:sqlite:", "");
    }

    // Cierra la concexion
    public void close (){
        try{
            if(connection != null && !connection.isClosed()){
                connection.close();
                System.out.println("Conexion a base de datos cerrada");
            }
        }catch (SQLException e){
            System.err.println("Error cerrando la conexion: " + e.getMessage());
        }
    }
    
    public void saveImage(String mediaId, long messageId, byte[] imageData, String mimeType, int width, int height) {
        String sql = """
            INSERT OR REPLACE INTO images (media_id, message_id, image_data, mime_type, width, height, created_at)
            VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
            """;
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, mediaId);
            pstmt.setLong(2, messageId);
            pstmt.setBytes(3, imageData);
            pstmt.setString(4, mimeType);
            pstmt.setInt(5, width);
            pstmt.setInt(6, height);
            pstmt.executeUpdate();
            System.out.println("Imagen guardada en DB local: " + mediaId);
        } catch (SQLException e) {
            System.err.println("Error guardando imagen: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public byte[] getImageData(String mediaId) {
        String sql = "SELECT image_data FROM images WHERE media_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, mediaId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("image_data");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error recuperando imagen: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    public boolean hasImage(String mediaId) {
        String sql = "SELECT 1 FROM images WHERE media_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, mediaId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Error verificando imagen: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    public void updateDownloaded(long messageId, boolean downloaded) {
        String sql = "UPDATE messages SET downloaded = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, downloaded ? 1 : 0);
            pstmt.setLong(2, messageId);
            pstmt.executeUpdate();
            System.out.println("Actualizado downloaded=" + downloaded + " para mensaje " + messageId);
        } catch (SQLException e) {
            System.err.println("Error actualizando downloaded: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void deleteImageFile(String mediaId) {
        try {
            String userHome = System.getProperty("user.home");
            String imagesDir = userHome + File.separator + ".chat-client" + File.separator + "images";
            File dir = new File(imagesDir);
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.startsWith(mediaId));
                if (files != null) {
                    for (File f : files) {
                        if (f.delete()) {
                            System.out.println("Imagen eliminada: " + f.getName());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error al eliminar archivo de imagen: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public void deleteImage(String mediaId) {
        String sql = "DELETE FROM images WHERE media_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, mediaId);
            pstmt.executeUpdate();
            System.out.println("Registro de imagen eliminado: " + mediaId);
        } catch (SQLException e) {
            System.err.println("Error eliminando imagen de BD: " + e.getMessage());
            e.printStackTrace();
        }
    }

}
