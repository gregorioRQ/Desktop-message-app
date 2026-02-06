package com.basic_chat.profile_service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.protobuf.ProtobufHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;

import com.basic_chat.profile_service.controller.ProfileController;
import com.basic_chat.profile_service.service.JwtService;
import com.basic_chat.profile_service.service.ProfileService;
import com.basic_chat.proto.LoginProto.LoginRequest;
import com.basic_chat.proto.LoginProto.LoginResponse;
import com.basic_chat.proto.LoginProto.TokenPair;
import com.basic_chat.proto.RefreshTokenMessage.RefreshRequest;
import com.basic_chat.proto.RefreshTokenMessage.RefreshResponse;
import com.basic_chat.proto.RegisterProto.RegisterRequest;
import com.basic_chat.proto.RegisterProto.RegisterResponse;

@WebMvcTest(ProfileController.class)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProfileService profileService;

    @MockBean
    private JwtService jwtService;

    // Configuración necesaria para que MockMvc pueda serializar/deserializar Protobuf en el contexto del test
    @Configuration
    static class TestConfig {
        @Bean
        public ProtobufHttpMessageConverter protobufHttpMessageConverter() {
            return new ProtobufHttpMessageConverter();
        }
    }

    private static final String PROTOBUF_CONTENT_TYPE = "application/x-protobuf";

    @Test
    @DisplayName("Register: Debería retornar 201 Created cuando el registro es exitoso")
    void register_Success() throws Exception {
        // Arrange
        RegisterRequest request = RegisterRequest.newBuilder()
                .setUsername("newUser")
                .setPassword("password123")
                .build();

        RegisterResponse response = RegisterResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Usuario registrado exitosamente")
                .setUserId("user-123")
                .build();

        when(profileService.registerUser(any(RegisterRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/profile/api/v1/register")
                .contentType(PROTOBUF_CONTENT_TYPE)
                .content(request.toByteArray()))
                .andExpect(status().isCreated())
                .andExpect(result -> {
                    // Verificamos manualmente el contenido de la respuesta protobuf
                    RegisterResponse actualResponse = RegisterResponse.parseFrom(result.getResponse().getContentAsByteArray());
                    assert actualResponse.getSuccess();
                    assert actualResponse.getUserId().equals("user-123");
                });
    }

    @Test
    @DisplayName("Register: Debería retornar 400 Bad Request cuando el registro falla")
    void register_Failure() throws Exception {
        // Arrange
        RegisterRequest request = RegisterRequest.newBuilder()
                .setUsername("existingUser")
                .setPassword("pass")
                .build();

        RegisterResponse response = RegisterResponse.newBuilder()
                .setSuccess(false)
                .setMessage("El usuario ya existe")
                .build();

        when(profileService.registerUser(any(RegisterRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/profile/api/v1/register")
                .contentType(PROTOBUF_CONTENT_TYPE)
                .content(request.toByteArray()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Login: Debería retornar 200 OK cuando el login es exitoso")
    void login_Success() throws Exception {
        // Arrange
        LoginRequest request = LoginRequest.newBuilder()
                .setUsername("validUser")
                .setPassword("validPass")
                .build();

        LoginResponse response = LoginResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Login exitoso")
                .setTokens(TokenPair.newBuilder()
                        .setAccessToken("jwt-token")
                        .setRefreshToken("refresh-token")
                        .build())
                .setUserId("user-123")
                .build();

        when(profileService.login(any(LoginRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/profile/api/v1/auth/login")
                .contentType(PROTOBUF_CONTENT_TYPE)
                .content(request.toByteArray()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Login: Debería retornar 401 Unauthorized cuando el login falla")
    void login_Failure() throws Exception {
        // Arrange
        LoginRequest request = LoginRequest.newBuilder().build();
        LoginResponse response = LoginResponse.newBuilder().setSuccess(false).build();

        when(profileService.login(any(LoginRequest.class))).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/profile/api/v1/auth/login")
                .contentType(PROTOBUF_CONTENT_TYPE)
                .content(request.toByteArray()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Refresh: Debería retornar 200 OK con nuevos tokens")
    void refresh_Success() throws Exception {
        // Arrange
        RefreshRequest request = RefreshRequest.newBuilder()
                .setToken("valid-refresh-token")
                .build();

        RefreshResponse response = RefreshResponse.newBuilder()
                .setAccessToken("new-access-token")
                .setRefreshToken("valid-refresh-token")
                .build();

        when(jwtService.refreshAccessToken(request.getToken())).thenReturn(response);

        // Act & Assert
        mockMvc.perform(post("/profile/api/v1/auth/refresh")
                .contentType(PROTOBUF_CONTENT_TYPE)
                .content(request.toByteArray()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    RefreshResponse actual = RefreshResponse.parseFrom(result.getResponse().getContentAsByteArray());
                    assert actual.getAccessToken().equals("new-access-token");
                });
    }
}