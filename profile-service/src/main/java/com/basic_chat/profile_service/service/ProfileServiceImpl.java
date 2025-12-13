package com.basic_chat.profile_service.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.basic_chat.profile_service.exception.UserAlreadyExistsException;
import java.util.Optional;
import com.basic_chat.profile_service.models.User;
import com.basic_chat.profile_service.repository.UserRepository;
import com.basic_chat.proto.AuthProto.AuthResponse;
import com.basic_chat.proto.LoginProto.LoginRequest;
import com.basic_chat.proto.LoginProto.LoginResponse;
import com.basic_chat.proto.RegisterProto.RegisterRequest;
import com.basic_chat.proto.RegisterProto.RegisterResponse;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService{

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Override
    @Transactional
    public RegisterResponse registerUser(RegisterRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        
        log.info("Iniciando registro para usuario: {}", username);
        
        // Validar que el username no exista
        if (userRepository.existsByUsername(username)) {
            log.warn("Intento de registro con username existente: {}", username);
            throw new UserAlreadyExistsException("El nombre de usuario ya está en uso");
        }
        
        // Validar longitud mínima de username
        if (username.length() < 3) {
            log.warn("Username demasiado corto: {}", username);
            return RegisterResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("El nombre de usuario debe tener al menos 3 caracteres")
                    .build();
        }
        
        // Validar longitud mínima de password
        if (password.length() < 6) {
            log.warn("Password demasiado corto para usuario: {}", username);
            return RegisterResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("La contraseña debe tener al menos 6 caracteres")
                    .build();
        }
        
        // Validar caracteres permitidos en username
        if (!username.matches("^[a-zA-Z0-9_-]+$")) {
            log.warn("Username con caracteres no permitidos: {}", username);
            return RegisterResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("El nombre de usuario solo puede contener letras, números, guiones y guiones bajos")
                    .build();
        }
        
        try {
            // Encriptar password
            String encryptedPassword = passwordEncoder.encode(password);
            
            // Crear nuevo usuario
            User user = User.builder()
                    .username(username)
                    .password(encryptedPassword)
                    .isActive(true)
                    .build();
            
            // Guardar en la base de datos
            User savedUser = userRepository.save(user);
            
            log.info("Usuario registrado exitosamente: {} con ID: {}", username, savedUser.getId());
            
            return RegisterResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("Usuario registrado exitosamente")
                    .setUserId(savedUser.getId())
                    .build();
                    
        } catch (Exception e) {
            log.error("Error al registrar usuario: {}", username, e);
            return RegisterResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Error interno al registrar usuario")
                    .build();
        }
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        Optional<User> userOpt = userRepository.findByUsername(username);
        if(userOpt.isEmpty()){
            return LoginResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Usuario o contraseña incorrectos")
                .build();
        }

        User user = userOpt.get();

        if(!passwordEncoder.matches(password, user.getPassword())){
            return LoginResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Usuario o contraseña incorrectos")
                .build();
        }

        // Generar el token y añadirlo a la respuesta.
        String token = jwtService.generateToken(user.getId(), user.getUsername());
        
        return LoginResponse.newBuilder()
            .setSuccess(true)
            .setMessage("Login exitoso")
            .setUserId(user.getId())
            .setToken(token)
            .build();

    }
}
