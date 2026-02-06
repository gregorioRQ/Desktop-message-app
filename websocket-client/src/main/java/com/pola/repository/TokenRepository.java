package com.pola.repository;

import com.pola.model.Session;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.Arrays;
import java.util.Base64;


// Esta clase se encarga de gestionar la persistencia de los tokens
// y de encriptarlos usando AES antes de guardarlos.
public class TokenRepository {
    private final String dbUrl;
    // En producción, esta clave debería derivarse de algo único del sistema o usuario
    private static final String SECRET_KEY_SEED = "LocalSessionSecretKey_ChangeMeInProd";
    private SecretKeySpec secretKey;

    public TokenRepository() {
        this("jdbc:sqlite:session.db");
    }

    public TokenRepository(String dbUrl) {
        this.dbUrl = dbUrl;
        prepareKey();
        initializeTable();
    }

    private void prepareKey() {
        try {
            byte[] key = SECRET_KEY_SEED.getBytes(StandardCharsets.UTF_8);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            key = sha.digest(key);
            key = Arrays.copyOf(key, 16); // Usar 128 bits para AES
            secretKey = new SecretKeySpec(key, "AES");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS session (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                access_token TEXT,
                refresh_token TEXT,
                user_id TEXT,
                username TEXT,
                device_id TEXT,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            System.err.println("Error inicializando tabla de sesiones: " + e.getMessage());
        }
    }

    public void saveSession(Session session) {
        String sql = """
            INSERT OR REPLACE INTO session (id, access_token, refresh_token, user_id, username, device_id, updated_at)
            VALUES (1, ?, ?, ?, ?, ?, datetime('now'))
            """;
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, encrypt(session.getAccessToken()));
            pstmt.setString(2, encrypt(session.getRefreshToken()));
            pstmt.setString(3, session.getUserId());
            pstmt.setString(4, session.getUsername());
            pstmt.setString(5, session.getDeviceId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Session loadSession() {
        String sql = "SELECT access_token, refresh_token, user_id, username, device_id FROM session WHERE id = 1";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            if (rs.next()) {
                String accessToken = decrypt(rs.getString("access_token"));
                String refreshToken = decrypt(rs.getString("refresh_token"));
                
                if (accessToken != null && refreshToken != null) {
                    return new Session(
                        accessToken,
                        refreshToken,
                        rs.getString("user_id"),
                        rs.getString("username"),
                        rs.getString("device_id")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void clearSession() {
        String sql = "DELETE FROM session";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String encrypt(String strToEncrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            System.err.println("Error al encriptar: " + e.getMessage());
        }
        return null;
    }

    private String decrypt(String strToDecrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
        } catch (Exception e) {
            System.err.println("Error al desencriptar: " + e.getMessage());
        }
        return null;
    }
}