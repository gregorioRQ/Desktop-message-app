package com.pola.service;

import com.pola.model.Session;
import com.pola.proto.LoginProto.LoginRequest;
import com.pola.proto.LoginProto.LoginResponse;
import com.pola.proto.LoginProto.TokenPair;
import com.pola.proto.RefreshTokenMessage.RefreshRequest;
import com.pola.proto.RefreshTokenMessage.RefreshResponse;
import com.pola.repository.TokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private HttpService httpService;

    @Mock
    private TokenRepository tokenRepository;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(httpService, tokenRepository);
    }

    @Test
    @DisplayName("Login exitoso debe guardar sesión y retornarla")
    void testLoginSuccess() throws ExecutionException, InterruptedException {
        // Arrange
        String username = "testUser";
        String password = "password";
        String accessToken = "access123";
        String refreshToken = "refresh123";
        String userId = "user1";

        TokenPair tokenPair = TokenPair.newBuilder()
                .setAccessToken(accessToken)
                .setRefreshToken(refreshToken)
                .build();

        LoginResponse loginResponse = LoginResponse.newBuilder()
                .setSuccess(true)
                .setUserId(userId)
                .setTokens(tokenPair)
                .build();

        when(httpService.login(any(LoginRequest.class), eq(LoginResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(loginResponse));

        // Act
        Session session = authService.login(username, password).get();

        // Assert
        assertNotNull(session);
        assertEquals(accessToken, session.getAccessToken());
        assertEquals(userId, session.getUserId());
        
        // Verificar que se guardó en el repositorio
        verify(tokenRepository).saveSession(any(Session.class));
    }

    @Test
    @DisplayName("Login fallido debe lanzar excepción")
    void testLoginFailure() {
        // Arrange
        LoginResponse loginResponse = LoginResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Credenciales inválidas")
                .build();

        when(httpService.login(any(LoginRequest.class), eq(LoginResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(loginResponse));

        // Act & Assert
        ExecutionException exception = assertThrows(ExecutionException.class, () -> {
            authService.login("user", "pass").get();
        });
        assertTrue(exception.getCause().getMessage().contains("Credenciales inválidas"));
        verify(tokenRepository, never()).saveSession(any());
    }

    @Test
    @DisplayName("AutoLogin exitoso al refrescar token")
    void testTryAutoLoginSuccess() throws ExecutionException, InterruptedException {
        // Arrange
        Session storedSession = new Session("oldAccess", "oldRefresh", "user1", "user", "dev1");
        when(tokenRepository.loadSession()).thenReturn(storedSession);

        RefreshResponse refreshResponse = RefreshResponse.newBuilder()
                .setAccessToken("newAccess")
                .setRefreshToken("newRefresh")
                .build();

        when(httpService.refreshToken(any(RefreshRequest.class), eq(RefreshResponse.class)))
                .thenReturn(CompletableFuture.completedFuture(refreshResponse));

        // Act
        Session newSession = authService.tryAutoLogin().get();

        // Assert
        assertEquals("newAccess", newSession.getAccessToken());
        
        // Verificar que se guardó la nueva sesión
        ArgumentCaptor<Session> sessionCaptor = ArgumentCaptor.forClass(Session.class);
        verify(tokenRepository).saveSession(sessionCaptor.capture());
        assertEquals("newAccess", sessionCaptor.getValue().getAccessToken());
    }

    @Test
    @DisplayName("AutoLogin falla si no hay sesión guardada")
    void testTryAutoLoginNoSession() {
        when(tokenRepository.loadSession()).thenReturn(null);

        ExecutionException exception = assertThrows(ExecutionException.class, () -> authService.tryAutoLogin().get());
        assertEquals("No hay sesión guardada", exception.getCause().getMessage());
    }

    @Test
    @DisplayName("AutoLogin limpia sesión si el refresco falla")
    void testTryAutoLoginRefreshFails() {
        Session storedSession = new Session("oldAccess", "oldRefresh", "user1", "user", "dev1");
        when(tokenRepository.loadSession()).thenReturn(storedSession);

        // Simular fallo en la petición HTTP o respuesta inválida
        when(httpService.refreshToken(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(RefreshResponse.newBuilder().setErrorMsg("Invalid token").build()));

        assertThrows(ExecutionException.class, () -> authService.tryAutoLogin().get());
        verify(tokenRepository).clearSession();
    }
}