package com.basic_chat.profile_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.basic_chat.proto.RegisterProto.RegisterResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Manejador global de excepciones
 * Convierte excepciones en respuestas Protobuf apropiadas
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    /**
     * Maneja UserAlreadyExistsException
     */
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<RegisterResponse> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        log.warn("Usuario ya existe: {}", ex.getMessage());
        
        RegisterResponse response = RegisterResponse.newBuilder()
                .setSuccess(false)
                .setMessage(ex.getMessage())
                .build();
        
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(response);
    }
    
    /**
     * Maneja excepciones genéricas
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<RegisterResponse> handleGenericException(Exception ex) {
        log.error("Error inesperado", ex);
        
        RegisterResponse response = RegisterResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Error interno del servidor")
                .build();
        
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(response);
    }
}
