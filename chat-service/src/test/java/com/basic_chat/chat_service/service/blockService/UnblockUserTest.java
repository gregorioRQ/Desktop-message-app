package com.basic_chat.chat_service.service.blockService;

import com.basic_chat.chat_service.repository.ContactBlockRepository;
import com.basic_chat.chat_service.service.BlockService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnblockUserTest {

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
    void unblockUser_WhenBlockExists_ShouldReturnTrue() {
        // Given
        when(blockRepository.deleteByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED))
                .thenReturn(1L);

        // When
        boolean result = blockUserService.unblockUser(VALID_BLOCKER, VALID_BLOCKED);

        // Then
        assertTrue(result);
        verify(blockRepository).deleteByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED);
    }

    @Test
    void unblockUser_WhenMultipleBlocksExist_ShouldReturnTrue() {
        // Given - aunque no debería pasar por la constraint UNIQUE, es un edge case
        when(blockRepository.deleteByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED))
                .thenReturn(2L);

        // When
        boolean result = blockUserService.unblockUser(VALID_BLOCKER, VALID_BLOCKED);

        // Then
        assertTrue(result);
        verify(blockRepository).deleteByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED);
    }

    // ==================== EDGE CASES - VALIDACIÓN ====================

    @Test
    void unblockUser_WhenBlockerIsNull_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.unblockUser(null, VALID_BLOCKED);

        // Then
        assertFalse(result);
        verify(blockRepository, never()).deleteByBlockerAndBlocked(anyString(), anyString());
    }

    @Test
    void unblockUser_WhenBlockedIsNull_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.unblockUser(VALID_BLOCKER, null);

        // Then
        assertFalse(result);
        verify(blockRepository, never()).deleteByBlockerAndBlocked(anyString(), anyString());
    }

    @Test
    void unblockUser_WhenBothUsernamesAreNull_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.unblockUser(null, null);

        // Then
        assertFalse(result);
        verify(blockRepository, never()).deleteByBlockerAndBlocked(anyString(), anyString());
    }

    @Test
    void unblockUser_WhenBlockerIsEmpty_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.unblockUser("", VALID_BLOCKED);

        // Then
        assertFalse(result);
        verify(blockRepository, never()).deleteByBlockerAndBlocked(anyString(), anyString());
    }

    @Test
    void unblockUser_WhenBlockedIsEmpty_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.unblockUser(VALID_BLOCKER, "");

        // Then
        assertFalse(result);
        verify(blockRepository, never()).deleteByBlockerAndBlocked(anyString(), anyString());
    }

    @Test
    void unblockUser_WhenBlockerIsBlank_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.unblockUser("   ", VALID_BLOCKED);

        // Then
        assertFalse(result);
        verify(blockRepository, never()).deleteByBlockerAndBlocked(anyString(), anyString());
    }

    @Test
    void unblockUser_WhenBlockedIsBlank_ShouldReturnFalse() {
        // When
        boolean result = blockUserService.unblockUser(VALID_BLOCKER, "   ");

        // Then
        assertFalse(result);
        verify(blockRepository, never()).deleteByBlockerAndBlocked(anyString(), anyString());
    }

    // ==================== EDGE CASES - IDEMPOTENCIA ====================

    @Test
    void unblockUser_WhenBlockDoesNotExist_ShouldReturnTrue() {
        // Given
        when(blockRepository.deleteByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED))
                .thenReturn(0L);

        // When
        boolean result = blockUserService.unblockUser(VALID_BLOCKER, VALID_BLOCKED);

        // Then
        assertTrue(result);
        verify(blockRepository).deleteByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED);
    }

    // ==================== EDGE CASES - EXCEPCIONES ====================

    @Test
    void unblockUser_WhenRepositoryThrowsException_ShouldReturnFalse() {
        // Given
        when(blockRepository.deleteByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED))
                .thenThrow(new RuntimeException("Database error"));

        // When
        boolean result = blockUserService.unblockUser(VALID_BLOCKER, VALID_BLOCKED);

        // Then
        assertFalse(result);
        verify(blockRepository).deleteByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED);
    }

    @Test
    void unblockUser_WhenRepositoryThrowsDataAccessException_ShouldReturnFalse() {
        // Given
        when(blockRepository.deleteByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED))
                .thenThrow(new RuntimeException("Database connection timeout"));

        // When
        boolean result = blockUserService.unblockUser(VALID_BLOCKER, VALID_BLOCKED);

        // Then
        assertFalse(result);
        verify(blockRepository).deleteByBlockerAndBlocked(VALID_BLOCKER, VALID_BLOCKED);
    }

    // ==================== EDGE CASES - CASOS LÍMITE ADICIONALES ====================

    @Test
    void unblockUser_WhenUsernamesHaveSpecialCharacters_ShouldReturnTrue() {
        // Given
        String specialBlocker = "user@123";
        String specialBlocked = "user#456";
        when(blockRepository.deleteByBlockerAndBlocked(specialBlocker, specialBlocked))
                .thenReturn(1L);

        // When
        boolean result = blockUserService.unblockUser(specialBlocker, specialBlocked);

        // Then
        assertTrue(result);
        verify(blockRepository).deleteByBlockerAndBlocked(specialBlocker, specialBlocked);
    }

    @Test
    void unblockUser_WhenUsernamesAreTrimmed_ShouldWorkCorrectly() {
        // Given
        String blockerWithSpaces = "  user1  ";
        when(blockRepository.deleteByBlockerAndBlocked(blockerWithSpaces, VALID_BLOCKED))
                .thenReturn(1L);

        // When
        boolean result = blockUserService.unblockUser(blockerWithSpaces, VALID_BLOCKED);

        // Then
        assertTrue(result);
        verify(blockRepository).deleteByBlockerAndBlocked(blockerWithSpaces, VALID_BLOCKED);
    }

    @Test
    void unblockUser_WhenUsernamesAreVeryLong_ShouldReturnTrue() {
        // Given
        String longBlocker = "a".repeat(255);
        String longBlocked = "b".repeat(255);
        when(blockRepository.deleteByBlockerAndBlocked(longBlocker, longBlocked))
                .thenReturn(1L);

        // When
        boolean result = blockUserService.unblockUser(longBlocker, longBlocked);

        // Then
        assertTrue(result);
        verify(blockRepository).deleteByBlockerAndBlocked(longBlocker, longBlocked);
    }

    @Test
    void unblockUser_WhenUsernamesHaveUnicodeCharacters_ShouldReturnTrue() {
        // Given
        String unicodeBlocker = "用户1";
        String unicodeBlocked = "用户2";
        when(blockRepository.deleteByBlockerAndBlocked(unicodeBlocker, unicodeBlocked))
                .thenReturn(1L);

        // When
        boolean result = blockUserService.unblockUser(unicodeBlocker, unicodeBlocked);

        // Then
        assertTrue(result);
        verify(blockRepository).deleteByBlockerAndBlocked(unicodeBlocker, unicodeBlocked);
    }
}