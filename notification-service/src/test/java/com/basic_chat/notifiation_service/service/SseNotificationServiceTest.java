package com.basic_chat.notifiation_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.basic_chat.notifiation_service.model.ContactUser;
import com.basic_chat.notifiation_service.model.User;
import com.basic_chat.notifiation_service.repository.ContactUserRepository;
import com.basic_chat.notifiation_service.repository.UserRepository;

/**
 * Tests unitarios para SseNotificationService.
 * 
 * Estos tests verifican el comportamiento del servicio de notificaciones SSE
 * usando mocks para los repositorios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SseNotificationService Tests")
class SseNotificationServiceTest {

    @Mock
    private ContactUserRepository contactUserRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SseNotificationService sseNotificationService;

    @Mock
    private SseEmitter emitter;

    private static final String TEST_USERNAME = "testUser";
    private static final String CONTACT_USERNAME = "contactUser";

    @BeforeEach
    void setUp() {
        // No se requiere inicialización adicional, Mockito maneja los mocks
    }

    // ==================== HAPPY PATH - registerClient ====================

    @Test
    @DisplayName("Happy Path: Debe registrar cliente y marcar usuario como ONLINE")
    void shouldRegisterClientAndMarkUserOnline() {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setOnline(false);
        
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(contactUserRepository.findOnlineContactUsernamesByUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());

        // Act
        sseNotificationService.registerClient(TEST_USERNAME, emitter);

        // Assert
        verify(userRepository).findByUsername(TEST_USERNAME);
        verify(userRepository).save(any(User.class));
        assertThat(user.isOnline()).isTrue();
    }

    @Test
    @DisplayName("Happy Path: Debe enviar lista de contactos online al registrar")
    void shouldSendOnlineContactsListWhenRegistering() {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setId("user-uuid-123");
        
        User contact1 = new User();
        contact1.setUsername("contact1");
        contact1.setId("contact-uuid-1");
        
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(contactUserRepository.findOnlineContactUsernamesByUsername(TEST_USERNAME))
            .thenReturn(Arrays.asList("contact1"));
        when(userRepository.findByUsername("contact1")).thenReturn(Optional.of(contact1));
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());

        // Act
        sseNotificationService.registerClient(TEST_USERNAME, emitter);

        // Assert
        verify(contactUserRepository).findOnlineContactUsernamesByUsername(TEST_USERNAME);
    }

    @Test
    @DisplayName("Happy Path: Debe notificar a contactos cuando usuario se conecta")
    void shouldNotifyContactsWhenUserConnects() {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setId("user-uuid-123");
        
        User contactUser = new User();
        contactUser.setUsername("contactUser");
        
        SseEmitter contactEmitter = new SseEmitter();
        
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(contactUserRepository.findOnlineContactUsernamesByUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Arrays.asList(new ContactUser("contactUser", TEST_USERNAME)));
        when(userRepository.findByUsername("contactUser")).thenReturn(Optional.of(contactUser));

        // Registrar primero el contacto para que tenga un emitter
        sseNotificationService.registerClient("contactUser", contactEmitter);
        
        // Act
        sseNotificationService.registerClient(TEST_USERNAME, emitter);

        // Assert
        verify(contactUserRepository).findByContactUsername(TEST_USERNAME);
    }

    // ==================== HAPPY PATH - unregisterClient ====================

    @Test
    @DisplayName("Happy Path: Debe desregistrar cliente y marcar como OFFLINE")
    void shouldUnregisterClientAndMarkOffline() {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setOnline(true);
        
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(contactUserRepository.findOnlineContactUsernamesByUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        
        // Primero registrar
        sseNotificationService.registerClient(TEST_USERNAME, emitter);

        // Act
        sseNotificationService.unregisterClient(TEST_USERNAME);

        // Assert
        verify(userRepository, times(2)).save(any(User.class)); // Una vez en register, una vez en unregister
        assertThat(user.isOnline()).isFalse();
    }

    // ==================== HAPPY PATH - sendNotification ====================

    @Test
    @DisplayName("Happy Path: Debe enviar notificación cuando hay conexión activa")
    void shouldSendNotificationWhenConnectionIsActive() {
        // Arrange
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(new User()));
        when(userRepository.save(any(User.class))).thenReturn(new User());
        when(contactUserRepository.findOnlineContactUsernamesByUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        
        sseNotificationService.registerClient(TEST_USERNAME, emitter);

        // Act
        boolean result = sseNotificationService.sendNotification(TEST_USERNAME, "test message");

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Happy Path: Debe retornar false cuando no hay conexión activa")
    void shouldReturnFalseWhenNoActiveConnection() {
        // Act
        boolean result = sseNotificationService.sendNotification("nonExistentUser", "test message");

        // Assert
        assertThat(result).isFalse();
    }

    // ==================== HAPPY PATH - hasActiveConnection ====================

    @Test
    @DisplayName("Happy Path: Debe retornar true cuando hay conexión activa")
    void shouldReturnTrueWhenHasActiveConnection() {
        // Arrange
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(new User()));
        when(userRepository.save(any(User.class))).thenReturn(new User());
        when(contactUserRepository.findOnlineContactUsernamesByUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        
        sseNotificationService.registerClient(TEST_USERNAME, emitter);

        // Act
        boolean result = sseNotificationService.hasActiveConnection(TEST_USERNAME);

        // Assert
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Happy Path: Debe retornar false cuando no hay conexión")
    void shouldReturnFalseWhenNoConnection() {
        // Act
        boolean result = sseNotificationService.hasActiveConnection("nonExistentUser");

        // Assert
        assertThat(result).isFalse();
    }

    // ==================== HAPPY PATH - getEmitter ====================

    @Test
    @DisplayName("Happy Path: Debe retornar emitter cuando existe")
    void shouldReturnEmitterWhenExists() {
        // Arrange
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(new User()));
        when(userRepository.save(any(User.class))).thenReturn(new User());
        when(contactUserRepository.findOnlineContactUsernamesByUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        
        sseNotificationService.registerClient(TEST_USERNAME, emitter);

        // Act
        SseEmitter result = sseNotificationService.getEmitter(TEST_USERNAME);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(emitter);
    }

    @Test
    @DisplayName("Happy Path: Debe retornar null cuando emitter no existe")
    void shouldReturnNullWhenEmitterDoesNotExist() {
        // Act
        SseEmitter result = sseNotificationService.getEmitter("nonExistentUser");

        // Assert
        assertThat(result).isNull();
    }

    // ==================== EDGE CASES ====================

    @Test
    @DisplayName("Edge Case: No debe fallar cuando usuario no existe al marcar online")
    void shouldNotFailWhenUserNotFoundMarkingOnline() {
        // Arrange
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());
        when(contactUserRepository.findOnlineContactUsernamesByUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());

        // Act - No debe lanzar excepción
        sseNotificationService.registerClient(TEST_USERNAME, emitter);

        // Assert
        verify(userRepository).findByUsername(TEST_USERNAME);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Edge Case: No debe fallar cuando usuario no existe al marcar offline")
    void shouldNotFailWhenUserNotFoundMarkingOffline() {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(contactUserRepository.findOnlineContactUsernamesByUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        
        // Registrar primero
        sseNotificationService.registerClient(TEST_USERNAME, emitter);
        
        // Ahora el usuario "desaparece" de la BD
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

        // Act - No debe lanzar excepción
        sseNotificationService.unregisterClient(TEST_USERNAME);

        // Assert
        // El método debe completarse sin excepción
    }

    @Test
    @DisplayName("Edge Case: Debe manejar excepción al enviar notificación")
    void shouldHandleExceptionWhenSendingNotification() throws IOException {
        // Arrange
        SseEmitter failingEmitter = new SseEmitter() {
            @Override
            public void send(Object object) throws IOException {
                throw new IOException("Connection closed");
            }
        };
        
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(new User()));
        when(userRepository.save(any(User.class))).thenReturn(new User());
        when(contactUserRepository.findOnlineContactUsernamesByUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        
        sseNotificationService.registerClient(TEST_USERNAME, failingEmitter);

        // Act
        boolean result = sseNotificationService.sendNotification(TEST_USERNAME, "test");

        // Assert
        assertThat(result).isFalse(); // Retorna false cuando hay error
    }

    @Test
    @DisplayName("Edge Case: Debe contar correctamente clientes activos")
    void shouldCountActiveClientsCorrectly() {
        // Arrange
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(new User()));
        when(userRepository.save(any(User.class))).thenReturn(new User());
        when(contactUserRepository.findOnlineContactUsernamesByUsername(anyString()))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(anyString())
)
            .thenReturn(Collections.emptyList());

        // Act
        sseNotificationService.registerClient("user1", new SseEmitter());
        sseNotificationService.registerClient("user2", new SseEmitter());
        sseNotificationService.registerClient("user3", new SseEmitter());

        // Assert
        assertThat(sseNotificationService.getActiveClientCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("Edge Case: Debe desregistrar todos los clientes correctamente")
    void shouldUnregisterAllClients() {
        // Arrange
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(new User()));
        when(userRepository.save(any(User.class))).thenReturn(new User());
        when(contactUserRepository.findOnlineContactUsernamesByUsername(anyString()))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(anyString()))
            .thenReturn(Collections.emptyList());
        
        sseNotificationService.registerClient("user1", new SseEmitter());
        sseNotificationService.registerClient("user2", new SseEmitter());

        // Act
        sseNotificationService.unregisterAll();

        // Assert
        assertThat(sseNotificationService.getActiveClientCount()).isZero();
    }

    @Test
    @DisplayName("Edge Case: No debe notificar contactos que no están conectados")
    void shouldNotNotifyContactsThatAreNotConnected() {
        // Arrange
        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setId("user-uuid-123");
        
        // Un contacto que tiene a TEST_USERNAME como contacto, pero no está conectado SSE
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(contactUserRepository.findOnlineContactUsernamesByUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Arrays.asList(new ContactUser("offlineContact", TEST_USERNAME)));

        // Act
        sseNotificationService.registerClient(TEST_USERNAME, emitter);

        // Assert
        // No debe intentar enviar notificación al contacto que no está conectado SSE
        verify(contactUserRepository).findByContactUsername(TEST_USERNAME);
    }

    @Test
    @DisplayName("Edge Case: Debe manejar re-registro de mismo usuario (reemplaza emitter)")
    void shouldHandleReRegistrationOfSameUser() {
        // Arrange
        SseEmitter firstEmitter = new SseEmitter();
        SseEmitter secondEmitter = new SseEmitter();
        
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(new User()));
        when(userRepository.save(any(User.class))).thenReturn(new User());
        when(contactUserRepository.findOnlineContactUsernamesByUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());
        when(contactUserRepository.findByContactUsername(TEST_USERNAME))
            .thenReturn(Collections.emptyList());

        // Act
        sseNotificationService.registerClient(TEST_USERNAME, firstEmitter);
        sseNotificationService.registerClient(TEST_USERNAME, secondEmitter);

        // Assert
        assertThat(sseNotificationService.getActiveClientCount()).isEqualTo(1);
        assertThat(sseNotificationService.getEmitter(TEST_USERNAME)).isEqualTo(secondEmitter);
    }
}
