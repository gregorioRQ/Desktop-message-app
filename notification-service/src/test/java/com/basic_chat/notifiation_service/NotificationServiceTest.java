package com.basic_chat.notifiation_service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.basic_chat.notifiation_service.model.User;
import com.basic_chat.notifiation_service.model.UserContact;
import com.basic_chat.notifiation_service.repository.UserContactRepository;
import com.basic_chat.notifiation_service.repository.UserRepository;
import com.basic_chat.notifiation_service.service.NotificationService;
import com.basic_chat.notifiation_service.service.UserPresenceService;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserContactRepository userContactRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @Mock
    private UserPresenceService userPresenceService;

    // Mocks necesarios para el constructor pero no usados directamente en estos tests
    @Mock
    private com.basic_chat.notifiation_service.repository.NotificationRepository notificationRepository;
    @Mock
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private NotificationService notificationService;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        userA = new User();
        userA.setId("userA");

        userB = new User();
        userB.setId("userB");

        // Configurar comportamiento común de Redis
        //when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    @Test
    void addContact_HappyPath_ShouldCreateMutualRelations() {
        // Arrange
        when(userRepository.findById("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findById("userB")).thenReturn(Optional.of(userB));
        
        // Simular que no existen relaciones previas
        when(userContactRepository.findByUser(userA)).thenReturn(Collections.emptyList());
        when(userContactRepository.findByUser(userB)).thenReturn(Collections.emptyList());

        // Simular sesiones activas para verificar suscripciones
        when(listOperations.range("user:userA:sessions", 0, -1)).thenReturn(Arrays.asList("sessionA1"));
        when(listOperations.range("user:userB:sessions", 0, -1)).thenReturn(Arrays.asList("sessionB1"));

        // Act
        notificationService.addContact("userA", "userB");

        // Assert
        // 1. Verificar que se guardaron ambas relaciones (A->B y B->A)
        verify(userContactRepository, times(2)).save(any(UserContact.class));
        
        // 2. Verificar suscripciones cruzadas
        verify(userPresenceService).subscribeToContact("sessionA1", "userB");
        verify(userPresenceService).subscribeToContact("sessionB1", "userA");
    }

    @Test
    void addContact_SelfAdd_ShouldDoNothing() {
        // Act
        notificationService.addContact("userA", "userA");

        // Assert
        verify(userRepository, never()).findById(anyString());
        verify(userContactRepository, never()).save(any(UserContact.class));
    }

    @Test
    void addContact_UserNotFound_ShouldThrowException() {
        // Arrange
        when(userRepository.findById("userA")).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            notificationService.addContact("userA", "userB");
        });

        verify(userContactRepository, never()).save(any(UserContact.class));
    }

    @Test
    void addContact_ContactNotFound_ShouldLogAndReturn() {
        // Arrange
        when(userRepository.findById("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findById("userB")).thenReturn(Optional.empty());

        // Act
        notificationService.addContact("userA", "userB");

        // Assert
        // No debe guardar nada si el contacto destino no existe
        verify(userContactRepository, never()).save(any(UserContact.class));
    }

    @Test
    void addContact_RelationAlreadyExists_ShouldNotDuplicate() {
        // Arrange
        when(userRepository.findById("userA")).thenReturn(Optional.of(userA));
        when(userRepository.findById("userB")).thenReturn(Optional.of(userB));

        // Simular que userA YA tiene a userB
        UserContact existingContact = new UserContact(userA, userB);
        when(userContactRepository.findByUser(userA)).thenReturn(Collections.singletonList(existingContact));
        
        // Simular que userB NO tiene a userA (caso asimétrico)
        when(userContactRepository.findByUser(userB)).thenReturn(Collections.emptyList());

        // Act
        notificationService.addContact("userA", "userB");

        // Assert
        // Debe guardar solo 1 vez (la relación faltante B -> A)
        verify(userContactRepository, times(1)).save(any(UserContact.class));
    }

    @Test
    void removeContacts_HappyPath_ShouldRemoveRelationsAndUnsubscribe() {
        // Arrange
        String userId = "userA";
        String contactId = "userB";
        List<String> contactIds = Collections.singletonList(contactId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(userA));
        
        // Mock Directa
        UserContact directContact = new UserContact(userA, userB);
        when(userContactRepository.findByUser(userA)).thenReturn(Collections.singletonList(directContact));

        // Mock Inversa
        when(userRepository.findById(contactId)).thenReturn(Optional.of(userB));
        UserContact inverseContact = new UserContact(userB, userA);
        when(userContactRepository.findByUser(userB)).thenReturn(Collections.singletonList(inverseContact));

        // Act
        notificationService.removeContacts(userId, contactIds);

        // Assert
        verify(userContactRepository).delete(directContact);
        verify(userContactRepository).delete(inverseContact);
        verify(userPresenceService).unsubscribeBidirectional(userId, contactId);
        
        // Verificar respuesta exitosa
        verify(messagingTemplate).convertAndSend(eq("/topic/notifications/" + userId), any(Map.class));
    }

    @Test
    // Test para deleteDirectContact: Si no existe la relación, no debe intentar borrar nada
    void removeContacts_DirectRelationMissing_ShouldSkipDirectDelete() {
        // Arrange
        String userId = "userA";
        String contactId = "userB";
        List<String> contactIds = Collections.singletonList(contactId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(userA));
        
        // Mock: No existe relación directa (lista vacía)
        when(userContactRepository.findByUser(userA)).thenReturn(Collections.emptyList());

        // Mock: Existe relación inversa (para verificar que el flujo continúa)
        when(userRepository.findById(contactId)).thenReturn(Optional.of(userB));
        UserContact inverseContact = new UserContact(userB, userA);
        when(userContactRepository.findByUser(userB)).thenReturn(Collections.singletonList(inverseContact));

        // Act
        notificationService.removeContacts(userId, contactIds);

        // Assert
        // Verificar que NO se llamó a delete para la relación directa
        verify(userContactRepository, never()).delete(argThat(c -> c.getUser().getId().equals(userId)));
        
        // Verificar que SÍ se llamó a delete para la inversa (el flujo continuó)
        verify(userContactRepository).delete(inverseContact);
    }

    @Test
    // Test para deleteInverseContact: Si el usuario contacto no existe, no debe fallar
    void removeContacts_InverseUserMissing_ShouldSkipInverseDelete() {
        // Arrange
        String userId = "userA";
        String contactId = "userB";
        List<String> contactIds = Collections.singletonList(contactId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(userA));
        
        // Directa existe
        UserContact directContact = new UserContact(userA, userB);
        when(userContactRepository.findByUser(userA)).thenReturn(Collections.singletonList(directContact));

        // Inversa: Usuario contacto no existe
        when(userRepository.findById(contactId)).thenReturn(Optional.empty());

        // Act
        notificationService.removeContacts(userId, contactIds);

        // Assert
        verify(userContactRepository).delete(directContact);
        // Verificar que no se intentó buscar contactos de un usuario inexistente
        verify(userContactRepository, never()).findByUser(any(User.class));
    }

    @Test
    // Test para processContactRemoval: Si falla la eliminación inversa, debe capturar excepción y no limpiar Redis
    void removeContacts_InverseDeleteFails_ShouldSkipUnsubscribe() {
        // Arrange
        String userId = "userA";
        String contactId = "userB";
        List<String> contactIds = Collections.singletonList(contactId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(userA));
        
        // Directa OK
        UserContact directContact = new UserContact(userA, userB);
        when(userContactRepository.findByUser(userA)).thenReturn(Collections.singletonList(directContact));

        // Inversa OK setup
        when(userRepository.findById(contactId)).thenReturn(Optional.of(userB));
        UserContact inverseContact = new UserContact(userB, userA);
        when(userContactRepository.findByUser(userB)).thenReturn(Collections.singletonList(inverseContact));

        // Simular fallo crítico en eliminación inversa
        doThrow(new RuntimeException("DB Error")).when(userContactRepository).delete(inverseContact);

        // Act
        notificationService.removeContacts(userId, contactIds);

        // Assert
        verify(userContactRepository).delete(directContact);
        verify(userContactRepository).delete(inverseContact); // Se intentó borrar
        
        // El fallo en inversa debe ser capturado por processContactRemoval y abortar el resto (Redis)
        verify(userPresenceService, never()).unsubscribeBidirectional(anyString(), anyString());
    }
}