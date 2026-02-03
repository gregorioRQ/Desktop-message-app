package com.basic_chat.chat_service.service.blockService;

import com.basic_chat.chat_service.repository.ContactBlockRepository;
import com.basic_chat.chat_service.service.BlockService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IsBlockedTest {

    @Mock
    private ContactBlockRepository blockRepository;

    @InjectMocks
    private BlockService blockService;

    private static final String SENDER = "senderUser";
    private static final String RECIPIENT = "recipientUser";

    // ==================== HAPPY PATH ====================

    @Test
    void isBlocked_WhenRecipientHasBlockedSender_ShouldReturnTrue() {
        // Given: El RECIPIENT ha bloqueado al SENDER
        // Nota: isBlocked(sender, recipient) verifica existsByBlockerAndBlocked(recipient, sender)
        when(blockRepository.existsByBlockerAndBlocked(RECIPIENT, SENDER)).thenReturn(true);

        // When
        boolean result = blockService.isBlocked(SENDER, RECIPIENT);

        // Then
        assertTrue(result, "Debería retornar true si el destinatario bloqueó al remitente");
        verify(blockRepository).existsByBlockerAndBlocked(RECIPIENT, SENDER);
    }

    @Test
    void isBlocked_WhenNoBlockExists_ShouldReturnFalse() {
        // Given
        when(blockRepository.existsByBlockerAndBlocked(RECIPIENT, SENDER)).thenReturn(false);

        // When
        boolean result = blockService.isBlocked(SENDER, RECIPIENT);

        // Then
        assertFalse(result, "Debería retornar false si no hay bloqueo");
        verify(blockRepository).existsByBlockerAndBlocked(RECIPIENT, SENDER);
    }

    // ==================== EDGE CASES - VALIDACIÓN ====================

    @Test
    void isBlocked_WhenSenderIsNull_ShouldReturnFalse() {
        // When
        boolean result = blockService.isBlocked(null, RECIPIENT);

        // Then
        assertFalse(result);
        verifyNoInteractions(blockRepository);
    }

    @Test
    void isBlocked_WhenRecipientIsNull_ShouldReturnFalse() {
        // When
        boolean result = blockService.isBlocked(SENDER, null);

        // Then
        assertFalse(result);
        verifyNoInteractions(blockRepository);
    }

    @Test
    void isBlocked_WhenSenderIsEmpty_ShouldReturnFalse() {
        // When
        boolean result = blockService.isBlocked("", RECIPIENT);

        // Then
        assertFalse(result);
        verifyNoInteractions(blockRepository);
    }

    @Test
    void isBlocked_WhenRecipientIsBlank_ShouldReturnFalse() {
        // When
        boolean result = blockService.isBlocked(SENDER, "   ");

        // Then
        assertFalse(result);
        verifyNoInteractions(blockRepository);
    }

    // ==================== EDGE CASES - EXCEPCIONES (FAIL OPEN) ====================

    @Test
    void isBlocked_WhenRepositoryThrowsException_ShouldReturnFalse() {
        // Given
        // Simulamos un error de base de datos
        when(blockRepository.existsByBlockerAndBlocked(RECIPIENT, SENDER))
                .thenThrow(new RuntimeException("Database connection failed"));

        // When
        boolean result = blockService.isBlocked(SENDER, RECIPIENT);

        // Then
        // El servicio está diseñado para "fail open" (permitir envío) en caso de error
        assertFalse(result, "Debería retornar false (no bloqueado) en caso de excepción");
        verify(blockRepository).existsByBlockerAndBlocked(RECIPIENT, SENDER);
    }
}