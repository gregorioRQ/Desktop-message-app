package com.pola;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.InvalidKeyIdException;
import org.whispersystems.libsignal.SignalProtocolAddress;
import org.whispersystems.libsignal.ecc.Curve;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignalProtocolStore;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

public class SignalStoreImpl implements SignalProtocolStore {

    private static final Logger logger = Logger.getLogger(SignalStoreImpl.class.getName());
    private final String dbPath;
    private IdentityKeyPair identityKeyPair;
    private int registrationId;

    public SignalStoreImpl(String dbPath) {
        this.dbPath = dbPath;
        initDb();
    }

    private void initDb() {
        try (Connection conn = DbUtils.getConnection(dbPath);
                Statement stmt = conn.createStatement()) {

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS identity (id INTEGER PRIMARY KEY, publicKey BLOB, privateKey BLOB, registrationId INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS prekeys (id INTEGER PRIMARY KEY, record BLOB)");
            stmt.execute("CREATE TABLE IF NOT EXISTS signed_prekeys (id INTEGER PRIMARY KEY, record BLOB)");
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS sessions (name TEXT, deviceId INTEGER, record BLOB, PRIMARY KEY(name, deviceId))");
            stmt.execute("CREATE TABLE IF NOT EXISTS identities (name TEXT PRIMARY KEY, publicKey BLOB)");
            stmt.execute("CREATE TABLE IF NOT EXISTS messages (sender TEXT, receiver TEXT, content TEXT)");

            logger.info("DB local inicializada en " + dbPath);

        } catch (SQLException e) {
            logger.severe("Error inicializando DB local: " + e.getMessage());
        }
    }

    // ============ Messages =====================
    public void storeMessage(String sender, String receiver, String content) {
        String sql = "INSERT INTO messages (sender, receiver, content) VALUES (?, ?, ?)";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, sender);
            ps.setString(2, receiver);
            ps.setString(3, content);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Error guardando mensaje: " + e.getMessage());
        }
    }

    public List<String> getLocalMessages(String user) {
        List<String> messages = new ArrayList<>();
        String sql = "SELECT sender, content FROM messages WHERE receiver=?";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                messages.add("De " + rs.getString("sender") + ": " + rs.getString("content"));
            }
        } catch (SQLException e) {
            logger.warning("Error obteniendo mensajes: " + e.getMessage());
        }
        return messages;
    }

    // ============= IdentityKeyStore =====================

    @Override
    public IdentityKeyPair getIdentityKeyPair() {
        if (identityKeyPair != null) {
            return identityKeyPair;
        }
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement("SELECT publicKey, privateKey FROM identity LIMIT 1")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                this.identityKeyPair = new IdentityKeyPair(
                        new IdentityKey(rs.getBytes("publicKey"), 0),
                        Curve.decodePrivatePoint(rs.getBytes("privateKey")));
                return this.identityKeyPair;
            }
        } catch (Exception e) {
            logger.warning("No se pudo obtener IdentityKeyPair: " + e.getMessage());
        }
        return null;
    }

    @Override
    public int getLocalRegistrationId() {
        if (registrationId != 0) {
            return registrationId;
        }
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement("SELECT registrationId FROM identity LIMIT 1")) {
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                this.registrationId = rs.getInt("registrationId");
                return this.registrationId;
            }
        } catch (SQLException e) {
            logger.warning("Error obteniendo registrationId: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public boolean saveIdentity(SignalProtocolAddress address, IdentityKey identityKey) {
        String sql = "INSERT OR REPLACE INTO identities (name, publicKey) VALUES (?, ?)";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, address.getName());
            ps.setBytes(2, identityKey.serialize());
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.warning("Error guardando identidad: " + e.getMessage());
        }
        return false;
    }

    @Override
    public IdentityKey getIdentity(SignalProtocolAddress address) {
        String sql = "SELECT publicKey FROM identities WHERE name = ?";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, address.getName());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new IdentityKey(rs.getBytes("publicKey"), 0);
            }
        } catch (Exception e) {
            logger.warning("Error obteniendo identidad: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean isTrustedIdentity(SignalProtocolAddress address, IdentityKey identityKey, Direction direction) {
        return true; // simplificado
    }

    public void storeIdentityKeyPair(IdentityKeyPair pair, int regId) {
        String sql = "INSERT OR REPLACE INTO identity (id, publicKey, privateKey, registrationId) VALUES (1, ?, ?, ?)";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBytes(1, pair.getPublicKey().serialize());
            ps.setBytes(2, pair.getPrivateKey().serialize());
            ps.setInt(3, regId);
            ps.executeUpdate();
            this.identityKeyPair = pair;
            this.registrationId = regId;
            logger.info("IdentityKeyPair guardado en " + dbPath);
        } catch (SQLException e) {
            logger.severe("Error guardando IdentityKeyPair: " + e.getMessage());
        }
    }

    public void storeLocalRegistrationId(int id) {
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement("UPDATE identity SET registrationId=? WHERE id=1")) {
            ps.setInt(1, id);
            ps.executeUpdate();
            logger.info("RegistrationId guardado: " + id);
        } catch (SQLException e) {
            logger.severe("Error guardando registrationId: " + e.getMessage());
        }
    }

    public IdentityKeyPair loadIdentityKeyPair() {
        return getIdentityKeyPair();
    }

    // ============= PreKeyStore =====================

    @Override
    public PreKeyRecord loadPreKey(int preKeyId) throws InvalidKeyIdException {
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement("SELECT record FROM prekeys WHERE id=?")) {
            ps.setInt(1, preKeyId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PreKeyRecord(rs.getBytes("record"));
            }
        } catch (Exception e) {
            throw new InvalidKeyIdException("PreKey no encontrado: " + preKeyId);
        }
        throw new InvalidKeyIdException("PreKey no encontrado: " + preKeyId);
    }

    @Override
    public void storePreKey(int preKeyId, PreKeyRecord record) {
        String sql = "INSERT OR REPLACE INTO prekeys (id, record) VALUES (?, ?)";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, preKeyId);
            ps.setBytes(2, record.serialize());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Error guardando preKey: " + e.getMessage());
        }
    }

    @Override
    public boolean containsPreKey(int preKeyId) {
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM prekeys WHERE id=?")) {
            ps.setInt(1, preKeyId);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void removePreKey(int preKeyId) {
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement("DELETE FROM prekeys WHERE id=?")) {
            ps.setInt(1, preKeyId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Error eliminando preKey: " + e.getMessage());
        }
    }

    // ============= SignedPreKeyStore =====================

    @Override
    public SignedPreKeyRecord loadSignedPreKey(int signedPreKeyId) throws InvalidKeyIdException {
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement("SELECT record FROM signed_prekeys WHERE id=?")) {
            ps.setInt(1, signedPreKeyId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new SignedPreKeyRecord(rs.getBytes("record"));
            }
        } catch (Exception e) {
            throw new InvalidKeyIdException("SignedPreKey no encontrado: " + signedPreKeyId);
        }
        throw new InvalidKeyIdException("SignedPreKey no encontrado: " + signedPreKeyId);
    }

    @Override
    public List<SignedPreKeyRecord> loadSignedPreKeys() {
        List<SignedPreKeyRecord> list = new ArrayList<>();
        try (Connection conn = DbUtils.getConnection(dbPath);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT record FROM signed_prekeys")) {
            while (rs.next()) {
                list.add(new SignedPreKeyRecord(rs.getBytes("record")));
            }
        } catch (SQLException | IOException e) {
            logger.warning("Error cargando signedPreKeys: " + e.getMessage());
        }
        return list;
    }

    @Override
    public void storeSignedPreKey(int signedPreKeyId, SignedPreKeyRecord record) {
        String sql = "INSERT OR REPLACE INTO signed_prekeys (id, record) VALUES (?, ?)";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, signedPreKeyId);
            ps.setBytes(2, record.serialize());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Error guardando signedPreKey: " + e.getMessage());
        }
    }

    @Override
    public boolean containsSignedPreKey(int signedPreKeyId) {
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM signed_prekeys WHERE id=?")) {
            ps.setInt(1, signedPreKeyId);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void removeSignedPreKey(int signedPreKeyId) {
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement("DELETE FROM signed_prekeys WHERE id=?")) {
            ps.setInt(1, signedPreKeyId);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Error eliminando signedPreKey: " + e.getMessage());
        }
    }

    // ============= SessionStore =====================

    @Override
    public SessionRecord loadSession(SignalProtocolAddress address) {
        String sql = "SELECT record FROM sessions WHERE name=? AND deviceId=?";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, address.getName());
            ps.setInt(2, address.getDeviceId());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new SessionRecord(rs.getBytes("record"));
            }
        } catch (Exception e) {
            logger.warning("Error cargando sesión: " + e.getMessage());
        }
        return new SessionRecord();
    }

    @Override
    public List<Integer> getSubDeviceSessions(String name) {
        List<Integer> devices = new ArrayList<>();
        String sql = "SELECT deviceId FROM sessions WHERE name=?";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                devices.add(rs.getInt("deviceId"));
            }
        } catch (SQLException e) {
            logger.warning("Error obteniendo sub-sesiones: " + e.getMessage());
        }
        return devices;
    }

    @Override
    public void storeSession(SignalProtocolAddress address, SessionRecord record) {
        String sql = "INSERT OR REPLACE INTO sessions (name, deviceId, record) VALUES (?, ?, ?)";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, address.getName());
            ps.setInt(2, address.getDeviceId());
            ps.setBytes(3, record.serialize());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Error guardando sesión: " + e.getMessage());
        }
    }

    @Override
    public boolean containsSession(SignalProtocolAddress address) {
        String sql = "SELECT 1 FROM sessions WHERE name=? AND deviceId=?";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, address.getName());
            ps.setInt(2, address.getDeviceId());
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void deleteSession(SignalProtocolAddress address) {
        String sql = "DELETE FROM sessions WHERE name=? AND deviceId=?";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, address.getName());
            ps.setInt(2, address.getDeviceId());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Error eliminando sesión: " + e.getMessage());
        }
    }

    @Override
    public void deleteAllSessions(String name) {
        String sql = "DELETE FROM sessions WHERE name=?";
        try (Connection conn = DbUtils.getConnection(dbPath);
                PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("Error eliminando todas las sesiones: " + e.getMessage());
        }
    }

}
