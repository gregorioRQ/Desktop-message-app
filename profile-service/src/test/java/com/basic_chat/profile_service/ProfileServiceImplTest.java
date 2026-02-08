package com.basic_chat.profile_service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.basic_chat.profile_service.models.User;
import com.basic_chat.profile_service.repository.UserRepository;
import com.basic_chat.profile_service.service.CredentialsValidator;
import com.basic_chat.profile_service.service.CredentialsValidator;
import com.basic_chat.profile_service.service.JwtService;
import com.basic_chat.profile_service.service.ProfileServiceImpl;
import com.basic_chat.proto.LoginProto.LoginRequest;
import com.basic_chat.proto.LoginProto.LoginResponse;
import com.basic_chat.proto.RegisterProto.RegisterRequest;
import com.basic_chat.proto.RegisterProto.RegisterResponse;
import com.basic_chat.proto.LogoutProto.LogoutRequest;
import com.basic_chat.proto.LogoutProto.LogoutResponse;

@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private CredentialsValidator credentialsValidator;

    @InjectMocks
    private ProfileServiceImpl profileService;

    @Test
    @DisplayName("Happy Path: Debería registrar el usuario exitosamente cuando las credenciales son válidas")
    void registerUser_HappyPath() {
        // Arrange
        String username = "usuarioValido";
        String password = "passwordSeguro123";
        String encodedPassword = "passwordEncriptada";
        String userId = "user-uuid-123";

        RegisterRequest request = RegisterRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .build();

        // Mock de validación exitosa (retorna null)
        when(credentialsValidator.validateCredentials(username, password)).thenReturn(null);

        // Mock de encriptación
        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);

        // Mock del guardado en repositorio
        // Usamos un mock de User para simular el objeto retornado con ID
        User savedUserMock = mock(User.class);
        when(savedUserMock.getId()).thenReturn(userId);
        when(userRepository.save(any(User.class))).thenReturn(savedUserMock);

        // Act
        RegisterResponse response = profileService.registerUser(request);

        // Assert
        assertTrue(response.getSuccess(), "El registro debería ser exitoso");
        assertEquals("Usuario registrado exitosamente", response.getMessage());
        assertEquals(userId, response.getUserId());

        verify(credentialsValidator).validateCredentials(username, password);
        verify(passwordEncoder).encode(password);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Edge Case: Debería fallar si la validación de credenciales falla")
    void registerUser_ValidationFailure() {
        // Arrange
        String username = "us"; // inválido
        String password = "123";
        String errorMsg = "El nombre de usuario debe tener al menos 3 caracteres";

        RegisterRequest request = RegisterRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .build();

        // Mock de validación fallida
        when(credentialsValidator.validateCredentials(username, password)).thenReturn(errorMsg);

        // Act
        RegisterResponse response = profileService.registerUser(request);

        // Assert
        assertFalse(response.getSuccess());
        assertEquals(errorMsg, response.getMessage());

        // Verificamos que no se intentó guardar nada ni encriptar
        verify(credentialsValidator).validateCredentials(username, password);
        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("Edge Case: Debería manejar excepciones inesperadas del repositorio")
    void registerUser_RepositoryException() {
        // Arrange
        String username = "usuarioTest";
        String password = "password123";

        RegisterRequest request = RegisterRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .build();

        when(credentialsValidator.validateCredentials(username, password)).thenReturn(null);
        when(passwordEncoder.encode(password)).thenReturn("encoded");
        
        // Simulamos error en base de datos
        when(userRepository.save(any(User.class))).thenThrow(new RuntimeException("Error de conexión DB"));

        // Act
        RegisterResponse response = profileService.registerUser(request);

        // Assert
        assertFalse(response.getSuccess());
        assertEquals("Error interno al registrar usuario", response.getMessage());
        
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Login Happy Path: Debería loguear exitosamente y devolver token")
    void login_HappyPath() {
        // Arrange
        String username = "usuarioExistente";
        String password = "passwordCorrecto";
        String encodedPassword = "encodedPassword";
        String userId = "user-123";
        String accessToken = "jwt-access-token";
        String refreshToken = "jwt-refresh-token";
        String deviceId = "device-123";

        LoginRequest request = LoginRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .setDeviceId(deviceId)
                .build();

        User user = User.builder()
                .id(userId)
                .username(username)
                .password(encodedPassword)
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, encodedPassword)).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn(accessToken);
        when(jwtService.generateRefreshToken(user, deviceId)).thenReturn(refreshToken);

        // Act
        LoginResponse response = profileService.login(request);

        // Assert
        assertTrue(response.getSuccess());
        assertEquals("Login exitoso", response.getMessage());
        assertEquals(userId, response.getUserId());
        assertEquals(accessToken, response.getTokens().getAccessToken());
        assertEquals(refreshToken, response.getTokens().getRefreshToken());
    }

    @Test
    @DisplayName("Login Edge Case: Debería fallar si el usuario no existe")
    void login_UserNotFound() {
        // Arrange
        String username = "usuarioInexistente";
        String password = "anyPassword";

        LoginRequest request = LoginRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.empty());

        // Act
        LoginResponse response = profileService.login(request);

        // Assert
        assertFalse(response.getSuccess());
        assertEquals("Usuario o contraseña incorrectos", response.getMessage());
    }

    @Test
    @DisplayName("Login Edge Case: Debería fallar si la contraseña es incorrecta")
    void login_WrongPassword() {
        // Arrange
        String username = "usuarioExistente";
        String password = "passwordIncorrecto";
        String encodedPassword = "encodedPassword";

        LoginRequest request = LoginRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .build();

        User user = User.builder()
                .username(username)
                .password(encodedPassword)
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(password, encodedPassword)).thenReturn(false);

        // Act
        LoginResponse response = profileService.login(request);

        // Assert
        assertFalse(response.getSuccess());
        assertEquals("Usuario o contraseña incorrectos", response.getMessage());
    }

    @Test
    @DisplayName("Login Edge Case: Debería manejar excepciones inesperadas")
    void login_Exception() {
        // Arrange
        String username = "usuarioError";
        LoginRequest request = LoginRequest.newBuilder()
                .setUsername(username)
                .setPassword("pass")
                .build();

        when(userRepository.findByUsername(username)).thenThrow(new RuntimeException("DB Error"));

        // Act
        LoginResponse response = profileService.login(request);

        // Assert
        assertFalse(response.getSuccess());
        assertEquals("Error interno durante el login", response.getMessage());
    }

    @Test
    @DisplayName("Logout Happy Path: Debería eliminar el token y retornar éxito")
    void logout_HappyPath() {
        String token = "refresh-token-123";
        LogoutRequest request = LogoutRequest.newBuilder().setRefreToken(token).build();

        LogoutResponse response = profileService.logout(request);

        assertTrue(response.getSuccess());
        assertEquals("RefreshToken eliminado", response.getMessage());
        verify(jwtService).deleteRefreshToken(token);
    }

    @Test
    @DisplayName("Logout Edge Case: Debería fallar si el token está vacío")
    void logout_EmptyToken() {
        LogoutRequest request = LogoutRequest.newBuilder().setRefreToken("").build();

        LogoutResponse response = profileService.logout(request);

        assertFalse(response.getSuccess());
        assertEquals("Token no enviado", response.getMessage());
        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("Logout Edge Case: Debería manejar excepciones al eliminar")
    void logout_Exception() {
        String token = "token-error";
        LogoutRequest request = LogoutRequest.newBuilder().setRefreToken(token).build();

        doThrow(new RuntimeException("DB Error")).when(jwtService).deleteRefreshToken(token);

        LogoutResponse response = profileService.logout(request);

        assertFalse(response.getSuccess());
        assertEquals("No se pudo eliminar el refreshToken", response.getMessage());
    }
}