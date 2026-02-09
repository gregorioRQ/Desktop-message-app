package com.basic_chat.profile_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.basic_chat.profile_service.service.JwtService;
import com.basic_chat.profile_service.service.ProfileService;
import com.basic_chat.proto.LoginProto.LoginRequest;
import com.basic_chat.proto.LoginProto.LoginResponse;
import com.basic_chat.proto.LogoutProto.LogoutRequest;
import com.basic_chat.proto.LogoutProto.LogoutResponse;
import com.basic_chat.proto.RefreshTokenMessage.RefreshRequest;
import com.basic_chat.proto.RefreshTokenMessage.RefreshResponse;
import com.basic_chat.proto.RegisterProto.RegisterRequest;
import com.basic_chat.proto.RegisterProto.RegisterResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controlador REST para registro de usuarios
 * Principio SOLID: Single Responsibility - Solo maneja requests HTTP con protobuf
 */
@RestController
@RequestMapping("/profile/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final ProfileService profileService;
    private final JwtService jwtService;

    /**
     * Endpoint para registrar un nuevo usuario
     * POST /api/v1/register
     * Content-Type: application/x-protobuf
     * Body: RegisterRequest (Protobuf)
     * 
     * @param request Solicitud de registro en formato Protobuf
     * @return Respuesta de registro en formato Protobuf
     */
    @PostMapping(value = "/register", 
                 consumes = "application/x-protobuf",
                 produces = "application/x-protobuf")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        
        log.info("Recibida solicitud de registro para usuario: {}", request.getUsername());
        
        RegisterResponse response = profileService.registerUser(request);
        
        if (response.getSuccess()) {
            log.info("Registro exitoso para usuario: {}", request.getUsername());
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(response);
        } else {
            log.warn("Registro fallido para usuario: {} - Razón: {}", 
                    request.getUsername(), response.getMessage());
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(response);
        }
    }

    @PostMapping(value = "auth/login",
                consumes = "application/x-protobuf",
                produces =  "application/x-protobuf")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request){
        LoginResponse response = profileService.login(request);
        if(response.getSuccess()){
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }else{
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @PostMapping(value = "auth/refresh", consumes = "application/x-protobuf", produces = "application/x-protobuf")
    public ResponseEntity<RefreshResponse> refresh(@RequestBody RefreshRequest request) {
        RefreshResponse tokens = jwtService.refreshAccessToken(request.getToken());
        return ResponseEntity.ok(tokens);
    }

    @PostMapping(value = "auth/logout", consumes = "application/x-protobuf", produces = "application/x-protobuf")
    public ResponseEntity<LogoutResponse> logout(@RequestBody LogoutRequest request){
        if (request.getRefreToken().isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(LogoutResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Token no enviado")
                    .build());
        }

        LogoutResponse response = profileService.logout(request);
        if(response.getSuccess()){
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }else{
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}
