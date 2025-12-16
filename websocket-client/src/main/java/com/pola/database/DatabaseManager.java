package com.pola.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    // Gestor de base de datos

    private static final String DB_URL = "jdbc:sqlite:chat_client.db";
    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager(){
        initializeDatabase();
    }

    public static synchronized DatabaseManager getInstance(){
        if(instance == null){
            instance = new DatabaseManager();
        }
        return instance;
    }

    // Obtiene la conexión de la db
    public Connection getConnection() throws SQLException {
        if(connection == null || connection.isClosed()){
            connection = DriverManager.getConnection(DB_URL);
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
                    contact_user_id TEXT NOT NULL,
                    contact_username TEXT NOT NULL,
                    is_blocked INTEGER DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(user_id, contact_user_id)
                )
                """;
            
            // Tabla de mensajes
            String createMessagesTable = """
                CREATE TABLE IF NOT EXISTS messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    contact_id INTEGER NOT NULL,
                    content TEXT NOT NULL,
                    sender_id TEXT NOT NULL,
                    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    is_read INTEGER DEFAULT 0,
                    FOREIGN KEY (contact_id) REFERENCES contacts(id) ON DELETE CASCADE
                )
                """;
            
            // Índices para mejorar rendimiento
            String createContactsIndex = """
                CREATE INDEX IF NOT EXISTS idx_contacts_user_id 
                ON contacts(user_id)
                """;
            
            String createMessagesIndex = """
                CREATE INDEX IF NOT EXISTS idx_messages_contact_id 
                ON messages(contact_id, timestamp DESC)
                """;
            
            stmt.execute(createContactsTable);
            stmt.execute(createMessagesTable);
            stmt.execute(createContactsIndex);
            stmt.execute(createMessagesIndex);
            
            System.out.println("Base de datos inicializada correctamente");
            
        } catch (SQLException e) {
            System.err.println("Error inicializando base de datos: " + e.getMessage());
            e.printStackTrace();
        }
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

}
