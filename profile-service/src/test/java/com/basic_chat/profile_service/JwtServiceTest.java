package com.basic_chat.profile_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.basic_chat.profile_service.models.RefreshToken;
import com.basic_chat.profile_service.models.User;
import com.basic_chat.profile_service.repository.RefreshTokenRepository;
import com.basic_chat.profile_service.repository.UserRepository;
import com.basic_chat.profile_service.service.JwtService;
import com.basic_chat.proto.RefreshTokenMessage.RefreshResponse;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private UserRepository userRepository;

    private JwtService jwtService;
    
    // Copiamos la clave hardcodeada del servicio para poder simular ataques o expiraciones en los tests.
    // En un entorno real, esto debería inyectarse desde application.properties.
    private static final String SECRET_KEY = "your-256-bit-secret-key-here-change-in-production";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(refreshTokenRepository, userRepository);
    }

    @Test
    @DisplayName("Happy Path: Generar y validar token correctamente")
    void generateAndValidateToken() {
        // Arrange
        String userId = "user-123";
        String username = "testUser";
        User user = User.builder()
                .id(userId)
                .username(username)
                .build();

        // Act
        String token = jwtService.generateToken(user);

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // Validar extracción de datos
        Claims claims = jwtService.validateToken(token);
        assertEquals(userId, claims.getSubject());
        assertEquals(username, claims.get("username"));
        
        // Validar métodos helper
        assertEquals(userId, jwtService.getUserIdFromToken(token));
        assertEquals(username, jwtService.getUsernameFromToken(token));
    }

    @Test
    @DisplayName("Edge Case: Token expirado")
    void testExpiredToken() {
        // Arrange: Generamos manualmente un token expirado usando la misma clave
        SecretKey key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("user")
                .expiration(new Date(System.currentTimeMillis() - 1000)) // Expiró hace 1 segundo
                .signWith(key)
                .compact();

        // Act & Assert
        // isTokenExpired captura la excepción ExpiredJwtException y devuelve true
        assertTrue(jwtService.isTokenExpired(expiredToken), "El token debería estar marcado como expirado");
    }

    @Test
    @DisplayName("Edge Case: Token no expirado")
    void testNotExpiredToken() {
        User user = User.builder().id("u").username("n").build();
        String token = jwtService.generateToken(user);
        assertFalse(jwtService.isTokenExpired(token));
    }

    @Test
    @DisplayName("Edge Case: Token inválido (firma incorrecta)")
    void testInvalidSignature() {
        // Arrange: Generamos token con otra clave distinta
        String otherKeyString = "otra-clave-secreta-muy-segura-para-tests-123";
        SecretKey otherKey = Keys.hmacShaKeyFor(otherKeyString.getBytes(StandardCharsets.UTF_8));
        
        String forgedToken = Jwts.builder()
                .subject("hacker")
                .signWith(otherKey)
                .compact();

        // Act & Assert
        assertThrows(Exception.class, () -> jwtService.validateToken(forgedToken));
    }

    @Test
    @DisplayName("Edge Case: Token malformado")
    void testMalformedToken() {
        String malformedToken = "esto.no.es.un.token.valido";
        assertThrows(Exception.class, () -> jwtService.validateToken(malformedToken));
    }

    @Test
    @DisplayName("generateRefreshToken: Happy Path - Debería generar y guardar el token")
    void generateRefreshToken_HappyPath() {
        // Arrange
        String userId = "user-123";
        String deviceId = "device-abc";
        User user = User.builder().id(userId).build();

        // Act
        String token = jwtService.generateRefreshToken(user, deviceId);

        // Assert
        assertNotNull(token);
        assertFalse(token.isEmpty());

        // Verificamos que se haya llamado al repositorio para guardar
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        // Verificamos los datos del token guardado
        RefreshToken savedToken = captor.getValue();
        assertEquals(userId, savedToken.getUserId());
        assertEquals(deviceId, savedToken.getDeviceId());
        assertEquals(token, savedToken.getToken());
        assertNotNull(savedToken.getExpiryDate());
        assertTrue(savedToken.getExpiryDate().isAfter(LocalDateTime.now()));
    }

    @Test
    @DisplayName("generateRefreshToken: Edge Case - Null User lanza NullPointerException")
    void generateRefreshToken_NullUser() {
        assertThrows(NullPointerException.class, () -> {
            jwtService.generateRefreshToken(null, "device-1");
        });
    }

    @Test
    @DisplayName("refreshAccessToken: Happy Path - Token válido y usuario existe")
    void refreshAccessToken_HappyPath() {
        // Arrange
        String refreshTokenStr = "valid-refresh-token";
        String userId = "user-123";
        String username = "testUser";
        
        RefreshToken storedToken = new RefreshToken();
        storedToken.setToken(refreshTokenStr);
        storedToken.setUserId(userId);
        storedToken.setExpiryDate(LocalDateTime.now().plusDays(1)); // Futuro
        
        User user = User.builder().id(userId).username(username).build();
        
        when(refreshTokenRepository.findByToken(refreshTokenStr)).thenReturn(Optional.of(storedToken));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        
        // Act
        RefreshResponse response = jwtService.refreshAccessToken(refreshTokenStr);
        
        // Assert
        assertNotNull(response.getAccessToken());
        assertFalse(response.getAccessToken().isEmpty());
        assertEquals(refreshTokenStr, response.getRefreshToken());
        assertTrue(response.getErrorMsg().isEmpty());
    }

    @Test
    @DisplayName("refreshAccessToken: Error - Token no encontrado")
    void refreshAccessToken_TokenNotFound() {
        // Arrange
        String refreshTokenStr = "invalid-token";
        when(refreshTokenRepository.findByToken(refreshTokenStr)).thenReturn(Optional.empty());
        
        // Act
        RefreshResponse response = jwtService.refreshAccessToken(refreshTokenStr);
        
        // Assert
        assertTrue(response.getAccessToken().isEmpty());
        assertEquals("Refresh token invalido o no encontrado", response.getErrorMsg());
    }

    @Test
    @DisplayName("refreshAccessToken: Error - Token expirado")
    void refreshAccessToken_TokenExpired() {
        // Arrange
        String refreshTokenStr = "expired-token";
        RefreshToken storedToken = new RefreshToken();
        storedToken.setToken(refreshTokenStr);
        storedToken.setExpiryDate(LocalDateTime.now().minusDays(1)); // Pasado
        
        when(refreshTokenRepository.findByToken(refreshTokenStr)).thenReturn(Optional.of(storedToken));
        
        // Act
        RefreshResponse response = jwtService.refreshAccessToken(refreshTokenStr);
        
        // Assert
        assertTrue(response.getAccessToken().isEmpty());
        assertEquals("El token ha expirado", response.getErrorMsg());
    }

    @Test
    @DisplayName("refreshAccessToken: Error - Usuario no encontrado")
    void refreshAccessToken_UserNotFound() {
        // Arrange
        String refreshTokenStr = "valid-token-no-user";
        String userId = "user-404";
        
        RefreshToken storedToken = new RefreshToken();
        storedToken.setToken(refreshTokenStr);
        storedToken.setUserId(userId);
        storedToken.setExpiryDate(LocalDateTime.now().plusDays(1));
        
        when(refreshTokenRepository.findByToken(refreshTokenStr)).thenReturn(Optional.of(storedToken));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        
        // Act
        RefreshResponse response = jwtService.refreshAccessToken(refreshTokenStr);
        
        // Assert
        assertTrue(response.getAccessToken().isEmpty());
        assertEquals("Usuario no encontrado", response.getErrorMsg());
    }
}