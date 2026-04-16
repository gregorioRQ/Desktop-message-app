package com.basic_chat.notifiation_service.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.basic_chat.notifiation_service.model.ContactUser;
import com.basic_chat.notifiation_service.repository.ContactUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests unitarios para AddContactConsumer.
 * 
 * Este consumidor procesa eventos de RabbitMQ para crear relaciones
 * bidireccionales de contactos entre usuarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AddContactConsumer Tests")
class AddContactConsumerTest {

    @Mock
    private ContactUserRepository contactUserRepository;

    @InjectMocks
    private AddContactConsumer addContactConsumer;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
    }

    // ==================== HAPPY PATH ====================

    @Test
    @DisplayName("Happy Path: Debe crear relación bidireccional cuando el mensaje es válido")
    void shouldCreateBidirectionalRelationshipWhenMessageIsValid() {
        // Arrange
        String sender = "userA";
        String contactUsername = "userB";
        String jsonMessage = String.format("{\"sender\":\"%s\",\"contact_username\":\"%s\"}", sender, contactUsername);

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, times(1)).save(new ContactUser(sender, contactUsername));
        verify(contactUserRepository, times(1)).save(new ContactUser(contactUsername, sender));
    }

    @Test
    @DisplayName("Happy Path: Debe manejar usernames con caracteres especiales")
    void shouldHandleUsernamesWithSpecialCharacters() {
        // Arrange
        String sender = "user_123-ABC";
        String contactUsername = "user.test@email";
        String jsonMessage = String.format("{\"sender\":\"%s\",\"contact_username\":\"%s\"}", sender, contactUsername);

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, times(2)).save(any(ContactUser.class));
    }

    @Test
    @DisplayName("Happy Path: Debe manejar mensaje JSON con espacios extra")
    void shouldHandleJsonWithExtraWhitespace() {
        // Arrange
        String jsonMessage = "{ \"sender\" : \"userA\" , \"contact_username\" : \"userB\" }";

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, times(2)).save(any(ContactUser.class));
    }

    // ==================== EDGE CASES - NULL/EMPTY FIELDS ====================

    @Test
    @DisplayName("Edge Case: No debe crear registros cuando sender es null")
    void shouldNotCreateRecordsWhenSenderIsNull() {
        // Arrange
        String jsonMessage = "{\"sender\":null,\"contact_username\":\"userB\"}";

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, never()).save(any(ContactUser.class));
    }

    @Test
    @DisplayName("Edge Case: No debe crear registros cuando sender está vacío")
    void shouldNotCreateRecordsWhenSenderIsEmpty() {
        // Arrange
        String jsonMessage = "{\"sender\":\"\",\"contact_username\":\"userB\"}";

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, never()).save(any(ContactUser.class));
    }

    @Test
    @DisplayName("Edge Case: No debe crear registros cuando contact_username es null")
    void shouldNotCreateRecordsWhenContactUsernameIsNull() {
        // Arrange
        String jsonMessage = "{\"sender\":\"userA\",\"contact_username\":null}";

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, never()).save(any(ContactUser.class));
    }

    @Test
    @DisplayName("Edge Case: No debe crear registros cuando contact_username está vacío")
    void shouldNotCreateRecordsWhenContactUsernameIsEmpty() {
        // Arrange
        String jsonMessage = "{\"sender\":\"userA\",\"contact_username\":\"\"}";

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, never()).save(any(ContactUser.class));
    }

    @Test
    @DisplayName("Edge Case: No debe crear registros cuando ambos campos son null")
    void shouldNotCreateRecordsWhenBothFieldsAreNull() {
        // Arrange
        String jsonMessage = "{\"sender\":null,\"contact_username\":null}";

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, never()).save(any(ContactUser.class));
    }

    // ==================== EDGE CASES - JSON MALFORMED ====================

    @Test
    @DisplayName("Edge Case: No debe crear registros cuando el JSON está mal formado")
    void shouldNotCreateRecordsWhenJsonIsMalformed() {
        // Arrange
        String jsonMessage = "{\"sender\":\"userA\",\"contact_username\"}"; // Falta valor

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, never()).save(any(ContactUser.class));
    }

    @Test
    @DisplayName("Edge Case: No debe crear registros cuando el JSON está vacío")
    void shouldNotCreateRecordsWhenJsonIsEmpty() {
        // Arrange
        String jsonMessage = "{}";

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, never()).save(any(ContactUser.class));
    }

    @Test
    @DisplayName("Edge Case: No debe crear registros cuando faltan campos requeridos")
    void shouldNotCreateRecordsWhenRequiredFieldsAreMissing() {
        // Arrange
        String jsonMessage = "{\"sender\":\"userA\"}"; // Falta contact_username

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, never()).save(any(ContactUser.class));
    }

    @Test
    @DisplayName("Edge Case: No debe crear registros cuando el input no es JSON")
    void shouldNotCreateRecordsWhenInputIsNotJson() {
        // Arrange
        String jsonMessage = "texto plano invalido";

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, never()).save(any(ContactUser.class));
    }

    // ==================== EDGE CASES - SAME USER ====================

    @Test
    @DisplayName("Edge Case: Debe permitir que un usuario se agregue a sí mismo (aunque no tiene sentido de negocio)")
    void shouldAllowSelfContactThoughNotRecommended() {
        // Arrange
        String username = "userA";
        String jsonMessage = String.format("{\"sender\":\"%s\",\"contact_username\":\"%s\"}", username, username);

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, times(2)).save(any(ContactUser.class));
    }

    // ==================== EDGE CASES - REPOSITORY EXCEPTIONS ====================

    @Test
    @DisplayName("Edge Case: Debe manejar excepción del repositorio en el primer save")
    void shouldHandleRepositoryExceptionOnFirstSave() {
        // Arrange
        String sender = "userA";
        String contactUsername = "userB";
        String jsonMessage = String.format("{\"sender\":\"%s\",\"contact_username\":\"%s\"}", sender, contactUsername);
        
        // Usar doThrow para cualquier argumento
        org.mockito.Mockito.doThrow(new RuntimeException("Database error"))
            .when(contactUserRepository).save(any(ContactUser.class));

        // Act - No debe lanzar excepción, solo loguear el error
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert - El catch debe manejar la excepción
        verify(contactUserRepository, times(1)).save(any(ContactUser.class));
    }

    @Test
    @DisplayName("Edge Case: Debe manejar excepción del repositorio en el segundo save")
    void shouldHandleRepositoryExceptionOnSecondSave() {
        // Arrange
        String sender = "userA";
        String contactUsername = "userB";
        String jsonMessage = String.format("{\"sender\":\"%s\",\"contact_username\":\"%s\"}", sender, contactUsername);
        
        // Primero guarda OK, segundo falla - usar doAnswer para controlar
        org.mockito.Mockito.doAnswer(invocation -> {
            ContactUser contact = invocation.getArgument(0);
            // Si es el primer contacto (sender -> contact), retornar OK
            if (contact.getUsername().equals(sender) && contact.getContactUsername().equals(contactUsername)) {
                return contact;
            }
            // Si es el segundo, lanzar excepción
            throw new RuntimeException("Database error");
        }).when(contactUserRepository).save(any(ContactUser.class));

        // Act - No debe lanzar excepción
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert - Debe intentar guardar ambos
        verify(contactUserRepository, times(2)).save(any(ContactUser.class));
    }

    // ==================== EDGE CASES - UNICODE/USERNAMES ====================

    @Test
    @DisplayName("Edge Case: Debe manejar usernames con caracteres Unicode")
    void shouldHandleUnicodeUsernames() {
        // Arrange
        String sender = "usuarioñ";
        String contactUsername = "用户测试";
        String jsonMessage = String.format("{\"sender\":\"%s\",\"contact_username\":\"%s\"}", sender, contactUsername);

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, times(2)).save(any(ContactUser.class));
    }

    @Test
    @DisplayName("Edge Case: Debe manejar usernames muy largos")
    void shouldHandleVeryLongUsernames() {
        // Arrange
        String sender = "a".repeat(255);
        String contactUsername = "b".repeat(255);
        String jsonMessage = String.format("{\"sender\":\"%s\",\"contact_username\":\"%s\"}", sender, contactUsername);

        // Act
        addContactConsumer.handleAddContact(jsonMessage);

        // Assert
        verify(contactUserRepository, times(2)).save(any(ContactUser.class));
    }
}
