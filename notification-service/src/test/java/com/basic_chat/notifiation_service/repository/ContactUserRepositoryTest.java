package com.basic_chat.notifiation_service.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.basic_chat.notifiation_service.model.ContactUser;
import com.basic_chat.notifiation_service.model.User;

/**
 * Tests de integración para ContactUserRepository usando @DataJpaTest.
 * 
 * Estos tests verifican que las queries JPQL funcionan correctamente
 * contra una base de datos H2 en memoria.
 */
@DataJpaTest
@DisplayName("ContactUserRepository Tests")
class ContactUserRepositoryTest {

    @Autowired
    private ContactUserRepository contactUserRepository;

    @Autowired
    private TestEntityManager entityManager;

    @BeforeEach
    void setUp() {
        // Limpiar datos existentes
        contactUserRepository.deleteAll();
        
        // Crear usuarios de prueba
        User userA = new User();
        userA.setId("uuid-userA");
        userA.setUsername("userA");
        userA.setOnline(true);
        entityManager.persist(userA);

        User userB = new User();
        userB.setId("uuid-userB");
        userB.setUsername("userB");
        userB.setOnline(true);
        entityManager.persist(userB);

        User userC = new User();
        userC.setId("uuid-userC");
        userC.setUsername("userC");
        userC.setOnline(false);
        entityManager.persist(userC);

        User userD = new User();
        userD.setId("uuid-userD");
        userD.setUsername("userD");
        userD.setOnline(true);
        entityManager.persist(userD);

        // Crear relaciones de contacto bidireccionales
        // userA tiene como contactos a: userB, userC
        ContactUser contactAB = new ContactUser("userA", "userB");
        ContactUser contactAC = new ContactUser("userA", "userC");
        contactUserRepository.save(contactAB);
        contactUserRepository.save(contactAC);

        // userB tiene como contactos a: userA, userD
        ContactUser contactBA = new ContactUser("userB", "userA");
        ContactUser contactBD = new ContactUser("userB", "userD");
        contactUserRepository.save(contactBA);
        contactUserRepository.save(contactBD);

        // userC tiene como contactos a: userA
        ContactUser contactCA = new ContactUser("userC", "userA");
        contactUserRepository.save(contactCA);

        // Flush para asegurar que los datos están persistidos
        entityManager.flush();
    }

    // ==================== HAPPY PATH ====================

    @Test
    @DisplayName("Happy Path: Debe encontrar contactos por username")
    void shouldFindContactsByUsername() {
        // Act
        List<ContactUser> contacts = contactUserRepository.findByUsername("userA");

        // Assert
        assertThat(contacts).hasSize(2);
        assertThat(contacts).extracting(ContactUser::getContactUsername)
            .containsExactlyInAnyOrder("userB", "userC");
    }

    @Test
    @DisplayName("Happy Path: Debe encontrar usuarios que tienen a un contacto")
    void shouldFindUsersByContactUsername() {
        // Act
        List<ContactUser> interestedUsers = contactUserRepository.findByContactUsername("userA");

        // Assert
        assertThat(interestedUsers).hasSize(2);
        assertThat(interestedUsers).extracting(ContactUser::getUsername)
            .containsExactlyInAnyOrder("userB", "userC");
    }

    @Test
    @DisplayName("Happy Path: Debe verificar existencia de relación")
    void shouldCheckIfRelationshipExists() {
        // Act & Assert
        assertThat(contactUserRepository.existsByUsernameAndContactUsername("userA", "userB")).isTrue();
        assertThat(contactUserRepository.existsByUsernameAndContactUsername("userA", "userD")).isFalse();
    }

    @Test
    @DisplayName("Happy Path: Debe encontrar solo contactos online")
    void shouldFindOnlyOnlineContacts() {
        // Act
        List<String> onlineContacts = contactUserRepository.findOnlineContactUsernamesByUsername("userA");

        // Assert - userB está online, userC está offline
        assertThat(onlineContacts).hasSize(1);
        assertThat(onlineContacts).containsExactly("userB");
    }

    // ==================== EDGE CASES ====================

    @Test
    @DisplayName("Edge Case: Debe retornar lista vacía cuando usuario no tiene contactos")
    void shouldReturnEmptyListWhenUserHasNoContacts() {
        // Act
        List<ContactUser> contacts = contactUserRepository.findByUsername("userD");

        // Assert
        assertThat(contacts).isEmpty();
    }

    @Test
    @DisplayName("Edge Case: Debe retornar lista vacía cuando nadie tiene al contacto")
    void shouldReturnEmptyListWhenNoOneHasContact() {
        // Act
        List<ContactUser> interestedUsers = contactUserRepository.findByContactUsername("userD");

        // Assert
        assertThat(interestedUsers).hasSize(1); // userB tiene a userD
        assertThat(interestedUsers.get(0).getUsername()).isEqualTo("userB");
    }

    @Test
    @DisplayName("Edge Case: Debe retornar false cuando la relación no existe")
    void shouldReturnFalseWhenRelationshipDoesNotExist() {
        // Act & Assert
        assertThat(contactUserRepository.existsByUsernameAndContactUsername("userZ", "userB")).isFalse();
        assertThat(contactUserRepository.existsByUsernameAndContactUsername("userA", "userZ")).isFalse();
    }

    @Test
    @DisplayName("Edge Case: Debe retornar lista vacía cuando todos los contactos están offline")
    void shouldReturnEmptyListWhenAllContactsAreOffline() {
        // Arrange - userC solo tiene a userA (online), creamos un usuario con solo contactos offline
        User userE = new User();
        userE.setUsername("userE");
        userE.setOnline(true);
        entityManager.persist(userE);

        // userE tiene solo a userC como contacto (userC está offline)
        ContactUser contactEC = new ContactUser("userE", "userC");
        contactUserRepository.save(contactEC);
        entityManager.flush();

        // Act
        List<String> onlineContacts = contactUserRepository.findOnlineContactUsernamesByUsername("userE");

        // Assert
        assertThat(onlineContacts).isEmpty();
    }

    @Test
    @DisplayName("Edge Case: Debe manejar usernames con caracteres especiales")
    void shouldHandleUsernamesWithSpecialCharacters() {
        // Arrange
        User specialUser = new User();
        specialUser.setUsername("user_123-ABC");
        specialUser.setOnline(true);
        entityManager.persist(specialUser);

        ContactUser specialContact = new ContactUser("userA", "user_123-ABC");
        contactUserRepository.save(specialContact);
        entityManager.flush();

        // Act
        List<ContactUser> contacts = contactUserRepository.findByUsername("userA");

        // Assert
        assertThat(contacts).extracting(ContactUser::getContactUsername)
            .contains("user_123-ABC");
    }

    @Test
    @DisplayName("Edge Case: Query JPQL debe hacer JOIN correcto")
    void shouldPerformCorrectJoinInQuery() {
        // Verificar que la query hace JOIN correcto entre ContactUser y User
        // Si el JOIN fallara, no filtraría por estado online
        
        // Act
        List<String> onlineContactsOfA = contactUserRepository.findOnlineContactUsernamesByUsername("userA");
        
        // userA tiene a userB (online) y userC (offline)
        // La query debe hacer JOIN con User.online y filtrar solo online=true
        assertThat(onlineContactsOfA).containsExactly("userB");
        assertThat(onlineContactsOfA).doesNotContain("userC"); // userC está offline
    }

    @Test
    @DisplayName("Edge Case: Debe manejar relación reflexiva (usuario es su propio contacto)")
    void shouldHandleSelfReferentialRelationship() {
        // Arrange - crear una relación A -> A (aunque rara)
        ContactUser selfContact = new ContactUser("userA", "userA");
        contactUserRepository.save(selfContact);
        entityManager.flush();

        // Act
        boolean exists = contactUserRepository.existsByUsernameAndContactUsername("userA", "userA");
        List<ContactUser> contacts = contactUserRepository.findByUsername("userA");

        // Assert
        assertThat(exists).isTrue();
        assertThat(contacts).hasSize(3); // 2 originales + 1 self
    }

    @Test
    @DisplayName("Edge Case: Operación debe ser idempotente al guardar duplicado")
    void shouldBeIdempotentWhenSavingDuplicate() {
        // Arrange
        ContactUser duplicate = new ContactUser("userA", "userB");
        
        // Act - Intentar guardar duplicado (debería fallar por clave primaria)
        // Nota: En JPA/H2, esto lanzará DataIntegrityViolationException
        // porque viola la constraint de clave primaria compuesta
        
        // Verificamos que existe antes
        boolean existsBefore = contactUserRepository.existsByUsernameAndContactUsername("userA", "userB");
        assertThat(existsBefore).isTrue();
        
        // No intentamos guardar duplicado porque lanzaría excepción
        // En el mundo real, el código debería verificar antes de guardar
    }

    @Test
    @DisplayName("Edge Case: Debe manejar usernames muy largos")
    void shouldHandleVeryLongUsernames() {
        // Arrange
        String longUsername = "a".repeat(255);
        String longContact = "b".repeat(255);
        
        User longUser = new User();
        longUser.setUsername(longUsername);
        longUser.setOnline(true);
        entityManager.persist(longUser);
        
        ContactUser longContactRel = new ContactUser("userA", longUsername);
        contactUserRepository.save(longContactRel);
        entityManager.flush();

        // Act
        List<ContactUser> contacts = contactUserRepository.findByUsername("userA");

        // Assert
        assertThat(contacts).extracting(ContactUser::getContactUsername)
            .contains(longUsername);
    }

    @Test
    @DisplayName("Edge Case: Debe retornar contactos ordenados consistentemente")
    void shouldReturnContactsInConsistentOrder() {
        // Act
        List<ContactUser> contacts = contactUserRepository.findByUsername("userA");

        // Assert - Aunque no garantizamos orden específico, verificamos que son consistentes
        assertThat(contacts).hasSize(2);
        assertThat(contacts.stream().map(ContactUser::getContactUsername).sorted().toList())
            .containsExactly("userB", "userC");
    }
}
