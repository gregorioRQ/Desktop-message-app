package com.basic_chat.notifiation_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.basic_chat.notifiation_service.model.ContactUser;
import com.basic_chat.notifiation_service.model.User;
import com.basic_chat.notifiation_service.repository.ContactUserRepository;
import com.basic_chat.notifiation_service.repository.UserRepository;
import com.basic_chat.notifiation_service.service.SseNotificationService;

/**
 * Tests unitarios para PresenceController usando @WebMvcTest.
 * 
 * Estos tests verifican los endpoints REST del controlador de presencia
 * usando mocks para los servicios y repositorios.
 */
@WebMvcTest(PresenceController.class)
@DisplayName("PresenceController Tests")
class PresenceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SseNotificationService sseNotificationService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ContactUserRepository contactUserRepository;

    private static final String TEST_USERNAME = "testUser";
    private static final String TEST_USER_ID = "user-uuid-123";
    private static final String CONTACT_USERNAME = "contactUser";

    @BeforeEach
    void setUp() {
        // Configuración común para los tests
    }

    // ==================== HAPPY PATH - POST /online ====================

    @Test
    @DisplayName("Happy Path: POST /online debe marcar usuario como ONLINE y notificar")
    void shouldMarkUserOnlineAndNotify() throws Exception {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setId(TEST_USER_ID);
        user.setOnline(false);

        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        when(sseNotificationService.hasActiveConnection(anyString())).thenReturn(false);

        String jsonRequest = String.format("{\"username\":\"%s\"}", TEST_USERNAME);

        // Act & Assert
        mockMvc.perform(post("/api/presence/online")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"));

        verify(userRepository).findByUsername(TEST_USERNAME);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Happy Path: POST /online debe notificar a contactos interesados")
    void shouldNotifyInterestedContactsWhenUserComesOnline() throws Exception {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setId(TEST_USER_ID);

        SseEmitter contactEmitter = new SseEmitter();

        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Arrays.asList(new ContactUser(CONTACT_USERNAME, TEST_USERNAME)));
        when(sseNotificationService.hasActiveConnection(CONTACT_USERNAME)).thenReturn(true);
        when(sseNotificationService.getEmitter(CONTACT_USERNAME)).thenReturn(contactEmitter);

        String jsonRequest = String.format("{\"username\":\"%s\"}", TEST_USERNAME);

        // Act & Assert
        mockMvc.perform(post("/api/presence/online")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isOk());

        verify(sseNotificationService).hasActiveConnection(CONTACT_USERNAME);
    }

    // ==================== HAPPY PATH - POST /offline ====================

    @Test
    @DisplayName("Happy Path: POST /offline debe marcar usuario como OFFLINE y notificar")
    void shouldMarkUserOfflineAndNotify() throws Exception {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setId(TEST_USER_ID);
        user.setOnline(true);

        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());

        String jsonRequest = String.format("{\"username\":\"%s\"}", TEST_USERNAME);

        // Act & Assert
        mockMvc.perform(post("/api/presence/offline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"));

        verify(userRepository).findByUsername(TEST_USERNAME);
        verify(userRepository).save(any(User.class));
    }

    // ==================== HAPPY PATH - GET /contacts ====================

    @Test
    @DisplayName("Happy Path: GET /contacts debe retornar lista de contactos")
    void shouldReturnContactsList() throws Exception {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setOnline(true);

        User contact = new User();
        contact.setUsername(CONTACT_USERNAME);
        contact.setId("contact-uuid-456");
        contact.setOnline(true);

        when(contactUserRepository.findByUsername(TEST_USERNAME))
            .thenReturn(Arrays.asList(new ContactUser(TEST_USERNAME, CONTACT_USERNAME)));
        when(userRepository.findByUsername(CONTACT_USERNAME)).thenReturn(Optional.of(contact));

        // Act & Assert
        mockMvc.perform(get("/api/presence/contacts")
                .header("X-Username", TEST_USERNAME))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/x-protobuf"));
    }

    @Test
    @DisplayName("Happy Path: GET /contacts debe retornar lista vacía si no hay contactos")
    void shouldReturnEmptyContactsList() throws Exception {
        // Arrange
        when(contactUserRepository.findByUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());

        // Act & Assert
        mockMvc.perform(get("/api/presence/contacts")
                .header("X-Username", TEST_USERNAME))
            .andExpect(status().isOk());
    }

    // ==================== HAPPY PATH - POST /test/presence ====================

    @Test
    @DisplayName("Happy Path: POST /test/presence debe simular cambio de presencia")
    void shouldSimulatePresenceChange() throws Exception {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setId(TEST_USER_ID);

        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());

        String jsonRequest = String.format("{\"username\":\"%s\",\"status\":\"ONLINE\"}", TEST_USERNAME);

        // Act & Assert
        mockMvc.perform(post("/api/presence/test/presence")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.username").value(TEST_USERNAME))
            .andExpect(jsonPath("$.state").value("ONLINE"));
    }

    // ==================== EDGE CASES - Usuario no encontrado ====================

    @Test
    @DisplayName("Edge Case: POST /online debe retornar 404 cuando usuario no existe")
    void shouldReturn404WhenUserNotFoundForOnline() throws Exception {
        // Arrange
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

        String jsonRequest = String.format("{\"username\":\"%s\"}", TEST_USERNAME);

        // Act & Assert
        mockMvc.perform(post("/api/presence/online")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isNotFound());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Edge Case: POST /offline debe retornar 404 cuando usuario no existe")
    void shouldReturn404WhenUserNotFoundForOffline() throws Exception {
        // Arrange
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

        String jsonRequest = String.format("{\"username\":\"%s\"}", TEST_USERNAME);

        // Act & Assert
        mockMvc.perform(post("/api/presence/offline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isNotFound());

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Edge Case: POST /test/presence debe retornar 404 cuando usuario no existe")
    void shouldReturn404WhenUserNotFoundForTestPresence() throws Exception {
        // Arrange
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

        String jsonRequest = String.format("{\"username\":\"%s\",\"status\":\"ONLINE\"}", TEST_USERNAME);

        // Act & Assert
        mockMvc.perform(post("/api/presence/test/presence")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isNotFound());
    }

    // ==================== EDGE CASES - Body inválido ====================

    @Test
    @DisplayName("Edge Case: POST /online debe manejar JSON inválido")
    void shouldHandleInvalidJsonForOnline() throws Exception {
        // Arrange
        String invalidJson = "{invalid json";

        // Act & Assert
        mockMvc.perform(post("/api/presence/online")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Edge Case: POST /offline debe manejar body vacío")
    void shouldHandleEmptyBodyForOffline() throws Exception {
        // Arrange
        String emptyJson = "{}";

        // Act & Assert - El username será null, pero el código debe manejarlo
        mockMvc.perform(post("/api/presence/offline")
                .contentType(MediaType.APPLICATION_JSON)
                .content(emptyJson))
            .andExpect(status().isNotFound()); // Porque findByUsername(null) retornará empty
    }

    // ==================== EDGE CASES - Headers ====================

    @Test
    @DisplayName("Edge Case: GET /contacts debe retornar 400 cuando falta header X-Username")
    void shouldReturn400WhenHeaderMissing() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/presence/contacts"))
            .andExpect(status().isBadRequest());
    }

    // ==================== EDGE CASES - Contactos ====================

    @Test
    @DisplayName("Edge Case: GET /contacts debe manejar contacto que no existe en users")
    void shouldHandleNonExistentContact() throws Exception {
        // Arrange
        when(contactUserRepository.findByUsername(TEST_USERNAME))
            .thenReturn(Arrays.asList(new ContactUser(TEST_USERNAME, "nonExistentContact")));
        when(userRepository.findByUsername("nonExistentContact")).thenReturn(Optional.empty());

        // Act & Assert
        mockMvc.perform(get("/api/presence/contacts")
                .header("X-Username", TEST_USERNAME))
            .andExpect(status().isOk()); // No debe fallar, simplemente no incluye ese contacto
    }

    // ==================== EDGE CASES - Error interno ====================

    @Test
    @DisplayName("Edge Case: Debe retornar 500 cuando hay error interno")
    void shouldReturn500OnInternalError() throws Exception {
        // Arrange
        when(userRepository.findByUsername(anyString()))
            .thenThrow(new RuntimeException("Database error"));

        String jsonRequest = String.format("{\"username\":\"%s\"}", TEST_USERNAME);

        // Act & Assert
        mockMvc.perform(post("/api/presence/online")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isInternalServerError());
    }

    // ==================== EDGE CASES - Estado OFFLINE ====================

    @Test
    @DisplayName("Edge Case: POST /test/presence debe manejar estado OFFLINE")
    void shouldHandleOfflineStatusInTestPresence() throws Exception {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setId(TEST_USER_ID);

        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());

        String jsonRequest = String.format("{\"username\":\"%s\",\"status\":\"OFFLINE\"}", TEST_USERNAME);

        // Act & Assert
        mockMvc.perform(post("/api/presence/test/presence")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("OFFLINE"));

        // Verificar que el usuario se marcó como offline
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Edge Case: POST /test/presence debe manejar status case-insensitive")
    void shouldHandleCaseInsensitiveStatus() throws Exception {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setId(TEST_USER_ID);

        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());

        // Probar con "online" en minúsculas
        String jsonRequest = String.format("{\"username\":\"%s\",\"status\":\"online\"}", TEST_USERNAME);

        // Act & Assert
        mockMvc.perform(post("/api/presence/test/presence")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonRequest))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("ONLINE"));
    }
}
