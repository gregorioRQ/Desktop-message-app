package com.basic_chat.notifiation_service.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests puros de Java para ContactUser y ContactUserId.
 * 
 * Estos tests verifican el comportamiento de la entidad y su clave compuesta
 * sin necesidad de Spring ni base de datos.
 */
@DisplayName("ContactUser Model Tests")
class ContactUserTest {

    // ==================== CONSTRUCTOR ====================

    @Test
    @DisplayName("Happy Path: Debe crear entidad con constructor parametrizado")
    void shouldCreateEntityWithParameterizedConstructor() {
        // Arrange
        String username = "userA";
        String contactUsername = "userB";

        // Act
        ContactUser contact = new ContactUser(username, contactUsername);

        // Assert
        assertThat(contact.getUsername()).isEqualTo(username);
        assertThat(contact.getContactUsername()).isEqualTo(contactUsername);
    }

    @Test
    @DisplayName("Happy Path: Debe crear entidad con constructor vacío")
    void shouldCreateEntityWithEmptyConstructor() {
        // Act
        ContactUser contact = new ContactUser();

        // Assert
        assertThat(contact.getUsername()).isNull();
        assertThat(contact.getContactUsername()).isNull();
    }

    // ==================== GETTERS Y SETTERS ====================

    @Test
    @DisplayName("Happy Path: Debe permitir modificar username con setter")
    void shouldAllowModifyingUsernameWithSetter() {
        // Arrange
        ContactUser contact = new ContactUser("userA", "userB");

        // Act
        contact.setUsername("newUser");

        // Assert
        assertThat(contact.getUsername()).isEqualTo("newUser");
    }

    @Test
    @DisplayName("Happy Path: Debe permitir modificar contactUsername con setter")
    void shouldAllowModifyingContactUsernameWithSetter() {
        // Arrange
        ContactUser contact = new ContactUser("userA", "userB");

        // Act
        contact.setContactUsername("newContact");

        // Assert
        assertThat(contact.getContactUsername()).isEqualTo("newContact");
    }

    // ==================== EQUALS ====================

    @Test
    @DisplayName("Happy Path: Dos entidades iguales deben ser iguales")
    void shouldBeEqualWhenBothFieldsAreEqual() {
        // Arrange
        ContactUser contact1 = new ContactUser("userA", "userB");
        ContactUser contact2 = new ContactUser("userA", "userB");

        // Assert
        assertThat(contact1).isEqualTo(contact2);
        assertThat(contact1.hashCode()).isEqualTo(contact2.hashCode());
    }

    @Test
    @DisplayName("Edge Case: Entidades con username diferente deben ser diferentes")
    void shouldNotBeEqualWhenUsernamesDiffer() {
        // Arrange
        ContactUser contact1 = new ContactUser("userA", "userB");
        ContactUser contact2 = new ContactUser("userC", "userB");

        // Assert
        assertThat(contact1).isNotEqualTo(contact2);
    }

    @Test
    @DisplayName("Edge Case: Entidades con contactUsername diferente deben ser diferentes")
    void shouldNotBeEqualWhenContactUsernamesDiffer() {
        // Arrange
        ContactUser contact1 = new ContactUser("userA", "userB");
        ContactUser contact2 = new ContactUser("userA", "userC");

        // Assert
        assertThat(contact1).isNotEqualTo(contact2);
    }

    @Test
    @DisplayName("Edge Case: Una entidad debe ser igual a sí misma")
    void shouldBeEqualToItself() {
        // Arrange
        ContactUser contact = new ContactUser("userA", "userB");

        // Assert
        assertThat(contact).isEqualTo(contact);
    }

    @Test
    @DisplayName("Edge Case: Debe retornar false al comparar con null")
    void shouldReturnFalseWhenComparingWithNull() {
        // Arrange
        ContactUser contact = new ContactUser("userA", "userB");

        // Assert
        assertThat(contact).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Edge Case: Debe retornar false al comparar con otro tipo")
    void shouldReturnFalseWhenComparingWithDifferentType() {
        // Arrange
        ContactUser contact = new ContactUser("userA", "userB");
        String other = "not a contact";

        // Assert
        assertThat(contact).isNotEqualTo(other);
    }

    // ==================== HASHCODE ====================

    @Test
    @DisplayName("Happy Path: Entidades iguales deben tener mismo hashCode")
    void shouldHaveSameHashCodeWhenEqual() {
        // Arrange
        ContactUser contact1 = new ContactUser("userA", "userB");
        ContactUser contact2 = new ContactUser("userA", "userB");

        // Assert
        assertThat(contact1.hashCode()).isEqualTo(contact2.hashCode());
    }

    @Test
    @DisplayName("Edge Case: Entidades diferentes pueden tener hashCode diferente")
    void mayHaveDifferentHashCodeWhenNotEqual() {
        // Arrange
        ContactUser contact1 = new ContactUser("userA", "userB");
        ContactUser contact2 = new ContactUser("userC", "userD");

        // Assert - No es requisito que sean diferentes, pero lo más probable
        // es que lo sean debido a la distribución del hash
        assertThat(contact1.hashCode()).isNotEqualTo(contact2.hashCode());
    }

    // ==================== CONTACTUSERID (Composite Key) ====================

    @Test
    @DisplayName("Happy Path: ContactUserId debe crearse con constructor parametrizado")
    void shouldCreateContactUserIdWithParameterizedConstructor() {
        // Act - Solo verificamos que no lanza excepción
        ContactUserId id = new ContactUserId("userA", "userB");

        // Assert - Verificamos que se creó (no hay getters, solo verificamos != null)
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("Happy Path: ContactUserId debe crearse con constructor vacío")
    void shouldCreateContactUserIdWithEmptyConstructor() {
        // Act
        ContactUserId id = new ContactUserId();

        // Assert
        assertThat(id).isNotNull();
    }

    @Test
    @DisplayName("Happy Path: ContactUserId iguales deben ser iguales")
    void contactUserIdShouldBeEqualWhenFieldsAreEqual() {
        // Arrange
        ContactUserId id1 = new ContactUserId("userA", "userB");
        ContactUserId id2 = new ContactUserId("userA", "userB");

        // Assert
        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    @DisplayName("Edge Case: ContactUserId diferentes deben no ser iguales")
    void contactUserIdShouldNotBeEqualWhenFieldsDiffer() {
        // Arrange
        ContactUserId id1 = new ContactUserId("userA", "userB");
        ContactUserId id2 = new ContactUserId("userA", "userC");

        // Assert
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    @DisplayName("Edge Case: ContactUserId debe ser igual a sí mismo")
    void contactUserIdShouldBeEqualToItself() {
        // Arrange
        ContactUserId id = new ContactUserId("userA", "userB");

        // Assert
        assertThat(id).isEqualTo(id);
    }

    @Test
    @DisplayName("Edge Case: ContactUserId debe retornar false con null")
    void contactUserIdShouldReturnFalseWhenComparedWithNull() {
        // Arrange
        ContactUserId id = new ContactUserId("userA", "userB");

        // Assert
        assertThat(id).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Edge Case: ContactUserId debe retornar false con otro tipo")
    void contactUserIdShouldReturnFalseWhenComparedWithDifferentType() {
        // Arrange
        ContactUserId id = new ContactUserId("userA", "userB");
        String other = "not an id";

        // Assert
        assertThat(id).isNotEqualTo(other);
    }

    // ==================== CASOS ESPECIALES ====================

    @Test
    @DisplayName("Edge Case: Debe manejar valores null en equals")
    void shouldHandleNullValuesInEquals() {
        // Arrange
        ContactUser contact1 = new ContactUser(null, "userB");
        ContactUser contact2 = new ContactUser(null, "userB");
        ContactUser contact3 = new ContactUser("userA", "userB");

        // Assert
        assertThat(contact1).isEqualTo(contact2); // Ambos null
        assertThat(contact1).isNotEqualTo(contact3); // Uno null, otro no
    }

    @Test
    @DisplayName("Edge Case: Debe manejar valores null en hashCode")
    void shouldHandleNullValuesInHashCode() {
        // Arrange
        ContactUser contact = new ContactUser(null, null);

        // Act
        int hashCode = contact.hashCode();

        // Assert - No debe lanzar excepción
        assertThat(hashCode).isNotNull();
    }

    @Test
    @DisplayName("Edge Case: Relación reflexiva (A -> A)")
    void shouldHandleSelfReferentialRelationship() {
        // Arrange
        ContactUser selfContact = new ContactUser("userA", "userA");

        // Assert
        assertThat(selfContact.getUsername()).isEqualTo(selfContact.getContactUsername());
    }

    @Test
    @DisplayName("Edge Case: Debe manejar usernames muy largos")
    void shouldHandleVeryLongUsernames() {
        // Arrange
        String longUsername = "a".repeat(1000);
        String longContact = "b".repeat(1000);

        // Act
        ContactUser contact = new ContactUser(longUsername, longContact);

        // Assert
        assertThat(contact.getUsername()).hasSize(1000);
        assertThat(contact.getContactUsername()).hasSize(1000);
    }

    @Test
    @DisplayName("Edge Case: Debe manejar caracteres especiales")
    void shouldHandleSpecialCharacters() {
        // Arrange
        String specialUsername = "user_123-ABC@email.com";
        String specialContact = "ñáéíóú_日本語";

        // Act
        ContactUser contact = new ContactUser(specialUsername, specialContact);

        // Assert
        assertThat(contact.getUsername()).isEqualTo(specialUsername);
        assertThat(contact.getContactUsername()).isEqualTo(specialContact);
    }

    @Test
    @DisplayName("Edge Case: ContactUserId debe manejar valores vacíos")
    void contactUserIdShouldHandleEmptyValues() {
        // Arrange
        ContactUserId id1 = new ContactUserId("", "");
        ContactUserId id2 = new ContactUserId("", "");
        ContactUserId id3 = new ContactUserId("userA", "userB");

        // Assert
        assertThat(id1).isEqualTo(id2);
        assertThat(id1).isNotEqualTo(id3);
    }

    @Test
    @DisplayName("Edge Case: ContactUserId hashCode con valores vacíos")
    void contactUserIdShouldHandleEmptyInHashCode() {
        // Arrange
        ContactUserId id = new ContactUserId("", "");

        // Act
        int hashCode = id.hashCode();

        // Assert - No debe lanzar excepción
        assertThat(hashCode).isNotNull();
    }
}
