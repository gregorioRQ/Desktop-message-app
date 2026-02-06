package com.pola.repository;

import com.pola.model.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class TokenRepositoryTest {

    private TokenRepository tokenRepository;
    private File tempDbFile;

    @BeforeEach
    void setUp() throws IOException {
        // Usamos un archivo temporal porque jdbc:sqlite::memory: borra la BD
        // cada vez que se cierra la conexión, y TokenRepository abre/cierra
        // conexiones en cada método.
        tempDbFile = File.createTempFile("test_session", ".db");
        tokenRepository = new TokenRepository("jdbc:sqlite:" + tempDbFile.getAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        if (tempDbFile != null && tempDbFile.exists()) {
            tempDbFile.delete();
        }
    }

    @Test
    @DisplayName("Debe guardar y cargar una sesión correctamente (Happy Path)")
    void testSaveAndLoadSession() {
        // Arrange
        Session session = new Session("access123", "refresh123", "user1", "juan", "device1");

        // Act
        tokenRepository.saveSession(session);
        Session loadedSession = tokenRepository.loadSession();

        // Assert
        assertNotNull(loadedSession, "La sesión cargada no debería ser nula");
        assertEquals("access123", loadedSession.getAccessToken());
        assertEquals("refresh123", loadedSession.getRefreshToken());
        assertEquals("user1", loadedSession.getUserId());
        assertEquals("juan", loadedSession.getUsername());
        assertEquals("device1", loadedSession.getDeviceId());
    }

    @Test
    @DisplayName("Debe sobrescribir la sesión existente si se guarda una nueva")
    void testSaveOverwritesExisting() {
        // Arrange
        Session session1 = new Session("oldAccess", "oldRefresh", "user1", "juan", "device1");
        tokenRepository.saveSession(session1);

        Session session2 = new Session("newAccess", "newRefresh", "user1", "juan", "device1");

        // Act
        tokenRepository.saveSession(session2);
        Session loadedSession = tokenRepository.loadSession();

        // Assert
        assertNotNull(loadedSession);
        assertEquals("newAccess", loadedSession.getAccessToken());
        assertEquals("newRefresh", loadedSession.getRefreshToken());
    }

    @Test
    @DisplayName("Debe eliminar la sesión correctamente")
    void testClearSession() {
        // Arrange
        tokenRepository.saveSession(new Session("a", "b", "c", "d", "e"));

        // Act
        tokenRepository.clearSession();
        Session loadedSession = tokenRepository.loadSession();

        // Assert
        assertNull(loadedSession, "La sesión debería ser nula después de limpiar");
    }
}