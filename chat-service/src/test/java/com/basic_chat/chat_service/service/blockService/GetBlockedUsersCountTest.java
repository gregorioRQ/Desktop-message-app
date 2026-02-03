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
class GetBlockedUsersCountTest {

    @Mock
    private ContactBlockRepository blockRepository;

    @InjectMocks
    private BlockService blockService;

    private static final String BLOCKER = "blockerUser";

    // ==================== HAPPY PATH ====================

    @Test
    void getBlockedUsersCount_WhenUsersAreBlocked_ShouldReturnCount() {
        // Given
        when(blockRepository.countByBlocker(BLOCKER)).thenReturn(5L);

        // When
        long count = blockService.getBlockedUsersCount(BLOCKER);

        // Then
        assertEquals(5L, count);
        verify(blockRepository).countByBlocker(BLOCKER);
    }

    @Test
    void getBlockedUsersCount_WhenNoUsersBlocked_ShouldReturnZero() {
        // Given
        when(blockRepository.countByBlocker(BLOCKER)).thenReturn(0L);

        // When
        long count = blockService.getBlockedUsersCount(BLOCKER);

        // Then
        assertEquals(0L, count);
        verify(blockRepository).countByBlocker(BLOCKER);
    }

    // ==================== EDGE CASES - VALIDACIÓN ====================

    @Test
    void getBlockedUsersCount_WhenBlockerIsNull_ShouldReturnZero() {
        assertEquals(0L, blockService.getBlockedUsersCount(null));
        verifyNoInteractions(blockRepository);
    }

    @Test
    void getBlockedUsersCount_WhenBlockerIsEmpty_ShouldReturnZero() {
        assertEquals(0L, blockService.getBlockedUsersCount(""));
        verifyNoInteractions(blockRepository);
    }

    // ==================== EDGE CASES - EXCEPCIONES ====================

    @Test
    void getBlockedUsersCount_WhenRepositoryThrowsException_ShouldReturnZero() {
        // Given
        when(blockRepository.countByBlocker(BLOCKER)).thenThrow(new RuntimeException("DB Error"));

        // When & Then
        long result = blockService.getBlockedUsersCount(BLOCKER);
        assertEquals(0L, result, "Debería retornar 0 en caso de error de base de datos");
    }
}