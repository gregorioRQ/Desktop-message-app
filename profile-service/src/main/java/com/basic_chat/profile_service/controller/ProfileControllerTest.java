package com.basic_chat.profile_service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.basic_chat.profile_service.service.JwtService;
import com.basic_chat.profile_service.service.ProfileService;
import com.basic_chat.proto.LogoutProto.LogoutRequest;
import com.basic_chat.proto.LogoutProto.LogoutResponse;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    private ProfileService profileService;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private ProfileController profileController;

    @Test
    @DisplayName("Logout: Debería retornar 400 Bad Request cuando el token está vacío")
    void logout_EmptyToken() {
        // Arrange
        LogoutRequest request = LogoutRequest.newBuilder().setRefreToken("").build();
        
        // Act
        ResponseEntity<LogoutResponse> response = profileController.logout(request);
        
        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Token no enviado", response.getBody().getMessage());
        assertEquals(false, response.getBody().getSuccess());
    }

    @Test
    @DisplayName("Logout: Debería retornar 200 OK cuando el servicio responde exitosamente")
    void logout_Success() {
        LogoutRequest request = LogoutRequest.newBuilder().setRefreToken("valid-token").build();
        LogoutResponse serviceResponse = LogoutResponse.newBuilder().setSuccess(true).setMessage("Exito").build();
        
        when(profileService.logout(request)).thenReturn(serviceResponse);
        
        ResponseEntity<LogoutResponse> response = profileController.logout(request);
        
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(serviceResponse, response.getBody());
    }

    @Test
    @DisplayName("Logout: Debería retornar 500 Internal Server Error cuando el servicio falla")
    void logout_ServiceFailure() {
        LogoutRequest request = LogoutRequest.newBuilder().setRefreToken("valid-token").build();
        LogoutResponse serviceResponse = LogoutResponse.newBuilder().setSuccess(false).setMessage("Error DB").build();
        
        when(profileService.logout(request)).thenReturn(serviceResponse);
        
        ResponseEntity<LogoutResponse> response = profileController.logout(request);
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(serviceResponse, response.getBody());
    }
}