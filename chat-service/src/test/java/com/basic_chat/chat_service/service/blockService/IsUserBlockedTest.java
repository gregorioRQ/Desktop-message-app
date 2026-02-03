package com.basic_chat.chat_service.service.blockService;

import com.basic_chat.chat_service.repository.ContactBlockRepository;
import com.basic_chat.chat_service.service.BlockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IsUserBlockedTest {

    @Mock
    private ContactBlockRepository blockRepository;

    @InjectMocks
    private BlockService blockService;

    private static final String BLOCKER = "blockerUser";
    private static final String POTENTIALLY_BLOCKED = "blockedUser";

    // ==================== HAPPY PATH ====================

    @Test
    void isUserBlocked_WhenUserIsBlocked_ShouldReturnTrue() {
        // Given
        when(blockRepository.existsByBlockerAndBlocked(BLOCKER, POTENTIALLY_BLOCKED)).thenReturn(true);

        // When
        boolean result = blockService.isUserBlocked(BLOCKER, POTENTIALLY_BLOCKED);

        // Then
        assertTrue(result);
        verify(blockRepository).existsByBlockerAndBlocked(BLOCKER, POTENTIALLY_BLOCKED);
    }

    @Test
    void isUserBlocked_WhenUserIsNotBlocked_ShouldReturnFalse() {
        // Given
        when(blockRepository.existsByBlockerAndBlocked(BLOCKER, POTENTIALLY_BLOCKED)).thenReturn(false);

        // When
        boolean result = blockService.isUserBlocked(BLOCKER, POTENTIALLY_BLOCKED);

        // Then
        assertFalse(result);
        verify(blockRepository).existsByBlockerAndBlocked(BLOCKER, POTENTIALLY_BLOCKED);
    }

    // ==================== EDGE CASES - VALIDACIÓN ====================

    @Test
    void isUserBlocked_WhenBlockerIsNull_ShouldReturnFalse() {
        assertFalse(blockService.isUserBlocked(null, POTENTIALLY_BLOCKED));
        verifyNoInteractions(blockRepository);
    }

    @Test
    void isUserBlocked_WhenBlockedUserIsNull_ShouldReturnFalse() {
        assertFalse(blockService.isUserBlocked(BLOCKER, null));
        verifyNoInteractions(blockRepository);
    }

    @Test
    void isUserBlocked_WhenBlockerIsEmpty_ShouldReturnFalse() {
        assertFalse(blockService.isUserBlocked("", POTENTIALLY_BLOCKED));
        verifyNoInteractions(blockRepository);
    }

    // ==================== EDGE CASES - EXCEPCIONES ====================

    @Test
    void isUserBlocked_WhenRepositoryThrowsException_ShouldReturnFalse() {
        // Given
        when(blockRepository.existsByBlockerAndBlocked(BLOCKER, POTENTIALLY_BLOCKED))
                .thenThrow(new RuntimeException("Connection failed"));

        // When & Then
        boolean result = blockService.isUserBlocked(BLOCKER, POTENTIALLY_BLOCKED);
        assertFalse(result, "Debería retornar false en caso de excepción");
    }
}