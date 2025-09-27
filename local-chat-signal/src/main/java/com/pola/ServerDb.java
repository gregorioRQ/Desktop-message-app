package com.pola;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;

public class ServerDb {
    private static final Logger logger = Logger.getLogger(ServerDb.class.getName());
    private static final String DB_URL = "jdbc:sqlite:server.db";

    static {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            Statement stmt = conn.createStatement();
            stmt.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "username TEXT PRIMARY KEY, " +
                    "registration_id INTEGER, " +
                    "device_id INTEGER, " +
                    "identity_key BLOB" +
                    ")");
            stmt.execute("CREATE TABLE IF NOT EXISTS prekeys (" +
                    "username TEXT, " +
                    "prekey_id INTEGER, " +
                    "prekey_public BLOB, " +
                    "signed_prekey_id INTEGER, " +
                    "signed_prekey_public BLOB, " +
                    "signed_prekey_signature BLOB, " +
                    "FOREIGN KEY(username) REFERENCES users(username))");
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "sender TEXT, " +
                    "receiver TEXT, " +
                    "message BLOB)");
            logger.info("Servidor inicializado con SQLite: server.db");
        } catch (SQLException e) {
            throw new RuntimeException("Error inicializando ServerDb", e);
        }
    }

    public static void saveUserWithKeys(String username, int registrationId, int deviceId,
            IdentityKey identityKey,
            int preKeyId, ECPublicKey preKeyPublic,
            int signedPreKeyId, ECPublicKey signedPreKeyPublic,
            byte[] signedPreKeySignature) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR REPLACE INTO users(username, registration_id, device_id, identity_key) VALUES (?, ?, ?, ?)");
            ps.setString(1, username);
            ps.setInt(2, registrationId);
            ps.setInt(3, deviceId);
            ps.setBytes(4, identityKey.serialize());
            ps.executeUpdate();

            ps = conn.prepareStatement("DELETE FROM prekeys WHERE username = ?");
            ps.setString(1, username);
            ps.executeUpdate();

            ps = conn.prepareStatement(
                    "INSERT INTO prekeys(username, prekey_id, prekey_public, signed_prekey_id, signed_prekey_public, signed_prekey_signature) "
                            +
                            "VALUES (?, ?, ?, ?, ?, ?)");
            ps.setString(1, username);
            ps.setInt(2, preKeyId);
            ps.setBytes(3, preKeyPublic.serialize());
            ps.setInt(4, signedPreKeyId);
            ps.setBytes(5, signedPreKeyPublic.serialize());
            ps.setBytes(6, signedPreKeySignature);
            ps.executeUpdate();

            logger.info("Guardadas claves públicas de " + username);
        } catch (Exception e) {
            throw new RuntimeException("Error guardando usuario con claves", e);
        }
    }

    public static PreKeyBundleDTO getPreKeyBundle(String username) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(
                    "SELECT u.registration_id, u.device_id, u.identity_key, " +
                            "p.prekey_id, p.prekey_public, p.signed_prekey_id, p.signed_prekey_public, p.signed_prekey_signature "
                            +
                            "FROM users u JOIN prekeys p ON u.username = p.username WHERE u.username = ?");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PreKeyBundleDTO(
                        rs.getInt("registration_id"),
                        rs.getInt("device_id"),
                        rs.getInt("prekey_id"),
                        rs.getBytes("prekey_public"),
                        rs.getInt("signed_prekey_id"),
                        rs.getBytes("signed_prekey_public"),
                        rs.getBytes("signed_prekey_signature"),
                        rs.getBytes("identity_key"));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error obteniendo PreKeyBundle", e);
        }
        return null;
    }

    public static void saveMessage(String sender, String receiver, byte[] message) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO messages(sender, receiver, message) VALUES (?, ?, ?)");
            ps.setString(1, sender);
            ps.setString(2, receiver);
            ps.setBytes(3, message);
            ps.executeUpdate();
            logger.info("Mensaje guardado: " + sender + " -> " + receiver);
        } catch (Exception e) {
            throw new RuntimeException("Error guardando mensaje", e);
        }
    }

    public static ResultSet getMessages(String receiver) throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        PreparedStatement ps = conn.prepareStatement(
                "SELECT sender, message FROM messages WHERE receiver = ?");
        ps.setString(1, receiver);
        return ps.executeQuery();
    }
}
