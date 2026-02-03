
package com.basic_chat.chat_service.service.blockService;

import com.basic_chat.chat_service.models.ContactBlock;
import com.basic_chat.chat_service.repository.ContactBlockRepository;
import com.basic_chat.chat_service.service.BlockService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockUserTest {

    @Mock
    private ContactBlockRepository blockRepository;

    @InjectMocks
    private BlockService blockUserService; // Cambia esto por el nombre real de tu clase Service

    private static final String VALID_BLOCKER = "user1";
    private static final String VALID_BLOCKED = "user2";

    @BeforeEach
    void setUp() {
        // Configuración común si es necesaria
    }

    // ==================== HAPPY PATH ====================

    @Test
    void blockUser_WhenValidUsersAndNotBlocked_ShouldReturnTrue() {
        // Given
        when(blockRepository.existsByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED))
                .thenReturn(false);
        when(blockRepository.save(any(ContactBlock.class)))
                .thenReturn(new ContactBlock(VALID_BLOCKER, VALID_BLOCKED));

        // When
        boolean result = blockUserService.blockUser(VALID_BLOCKER, VALID_BLOCKED);

        // Then
        assertTrue(result);
        verify(blockRepository).existsByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED);
        verify(blockRepository).save(any(ContactBlock.class));
    }

    // ==================== EDGE CASES - VALIDACIÓN ====================

    @Test
    void blockUser_WhenBlockerIsNull_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.blockUser(null, VALID_BLOCKED);

        // Then
        assertFalse(result);
        verify(blockRepository, never()).existsByBlockerAndBlocked(anyString(), anyString());
        verify(blockRepository, never()).save(any(ContactBlock.class));
    }

    @Test
    void blockUser_WhenBlockedIsNull_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.blockUser(VALID_BLOCKER, null);

        // Then
        assertFalse(result);
        verify(blockRepository, never()).existsByBlockerAndBlocked(anyString(), anyString());
        verify(blockRepository, never()).save(any(ContactBlock.class));
    }

    @Test
    void blockUser_WhenBothUsernamesAreNull_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.blockUser(null, null);

        // Then
        assertFalse(result);
        verify(blockRepository, never()).existsByBlockerAndBlocked(anyString(), anyString());
        verify(blockRepository, never()).save(any(ContactBlock.class));
    }

    @Test
    void blockUser_WhenBlockerIsEmpty_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.blockUser("", VALID_BLOCKED);

        // Then
        assertFalse(result);
        verify(blockRepository, never()).existsByBlockerAndBlocked(anyString(), anyString());
        verify(blockRepository, never()).save(any(ContactBlock.class));
    }

    @Test
    void blockUser_WhenBlockedIsEmpty_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.blockUser(VALID_BLOCKER, "");

        // Then
        assertFalse(result);
        verify(blockRepository, never()).existsByBlockerAndBlocked(anyString(), anyString());
        verify(blockRepository, never()).save(any(ContactBlock.class));
    }

    @Test
    void blockUser_WhenBlockerIsBlank_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.blockUser("   ", VALID_BLOCKED);

        // Then
        assertFalse(result);
        verify(blockRepository, never()).existsByBlockerAndBlocked(anyString(), anyString());
        verify(blockRepository, never()).save(any(ContactBlock.class));
    }

    @Test
    void blockUser_WhenBlockedIsBlank_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.blockUser(VALID_BLOCKER, "   ");

        // Then
        assertFalse(result);
        verify(blockRepository, never()).existsByBlockerAndBlocked(anyString(), anyString());
        verify(blockRepository, never()).save(any(ContactBlock.class));
    }

    // ==================== EDGE CASES - AUTO-BLOQUEO ====================

    @Test
    void blockUser_WhenUserTriesToBlockThemselves_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.blockUser(VALID_BLOCKER, VALID_BLOCKER);

        // Then
        assertFalse(result);
        verify(blockRepository, never()).existsByBlockerAndBlocked(anyString(), anyString());
        verify(blockRepository, never()).save(any(ContactBlock.class));
    }

    @Test
    void blockUser_WhenUserTriesToBlockThemselvesCaseInsensitive_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.blockUser("User1", "user1");

        // Then
        assertFalse(result);
        verify(blockRepository, never()).existsByBlockerAndBlocked(anyString(), anyString());
        verify(blockRepository, never()).save(any(ContactBlock.class));
    }

    // ==================== EDGE CASES - IDEMPOTENCIA ====================

    @Test
    void blockUser_WhenBlockAlreadyExists_ShouldReturnTrueWithoutSaving() {
        // Given
        when(blockRepository.existsByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED))
                .thenReturn(true);

        // When
        boolean result = blockUserService.blockUser(VALID_BLOCKER, VALID_BLOCKED);

        // Then
        assertTrue(result);
        verify(blockRepository).existsByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED);
        verify(blockRepository, never()).save(any(ContactBlock.class));
    }

    // ==================== EDGE CASES - EXCEPCIONES ====================

    @Test
    void blockUser_WhenRepositoryThrowsException_ShouldThrowRuntimeException() {
        // Given
        when(blockRepository.existsByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED))
                .thenReturn(false);
        when(blockRepository.save(any(ContactBlock.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            blockUserService.blockUser(VALID_BLOCKER, VALID_BLOCKED);
        });

        assertEquals("Database error", exception.getMessage());
        verify(blockRepository).existsByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED);
        verify(blockRepository).save(any(ContactBlock.class));
    }

    @Test
    void blockUser_WhenExistsByBlockerAndBlockedThrowsException_ShouldPropagateException() {
        // Given
        when(blockRepository.existsByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED))
                .thenThrow(new RuntimeException("Database connection error"));

        // When & Then
        assertThrows(RuntimeException.class, () -> {
            blockUserService.blockUser(VALID_BLOCKER, VALID_BLOCKED);
        });

        verify(blockRepository).existsByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED);
        verify(blockRepository, never()).save(any(ContactBlock.class));
    }

    // ==================== EDGE CASES - CASOS LÍMITE ADICIONALES ====================

    @Test
    void blockUser_WhenUsernamesHaveSpecialCharacters_ShouldReturnTrue() {
        // Given
        String specialBlocker = "user@123";
        String specialBlocked = "user#456";
        when(blockRepository.existsByBlockerAndBlocked(specialBlocker, specialBlocked))
                .thenReturn(false);
        when(blockRepository.save(any(ContactBlock.class)))
                .thenReturn(new ContactBlock(specialBlocker, specialBlocked));

        // When
        boolean result = blockUserService.blockUser(specialBlocker, specialBlocked);

        // Then
        assertTrue(result);
        verify(blockRepository).save(any(ContactBlock.class));
    }

    @Test
    void blockUser_WhenUsernamesAreTrimmed_ShouldWorkCorrectly() {
        // Given
        String blockerWithSpaces = "  user1  ";
        when(blockRepository.existsByBlockerAndBlocked(blockerWithSpaces, VALID_BLOCKED))
                .thenReturn(false);
        when(blockRepository.save(any(ContactBlock.class)))
                .thenReturn(new ContactBlock(blockerWithSpaces, VALID_BLOCKED));

        // When
        boolean result = blockUserService.blockUser(blockerWithSpaces, VALID_BLOCKED);

        // Then
        assertTrue(result);
        verify(blockRepository).save(any(ContactBlock.class));
    }
}