package com.basic_chat.profile_service.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import com.basic_chat.profile_service.models.User;
import com.basic_chat.profile_service.repository.UserRepository;
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
    private final CredentialsValidator credentialsValidator;

    /**
     * Registra un nuevo usuario en la base de datos.
     * 
     * 1. Valida las credenciales
     * 2. Encripta la contraseña.
     * 3. Guarda el usuario.
     * 4. Envia la respuesta al cliente.
     * 
     * @param request La solicitud protobuf con los datos del usuario.
     * @return RegisterResponse con mensaje de exito o de fracaso.
     */

    @Override
    @Transactional
    public RegisterResponse registerUser(RegisterRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();
        
        log.info("Iniciando registro para usuario: {}", username);
        
        // Validar credenciales
        String validationError = credentialsValidator.validateCredentials(username, password);
        if (validationError != null) {
            log.warn("Validación fallida para usuario: {} - {}", username, validationError);
            return RegisterResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(validationError)
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
            log.error("Error crítico al registrar usuario: {}", username, e);
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
        
        log.info("Iniciando login para usuario: {}", username);

        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            if(userOpt.isEmpty()){
                log.warn("Intento de login con usuario inexistente: {}", username);
                return LoginResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Usuario o contraseña incorrectos")
                    .build();
            }

            User user = userOpt.get();

            if(!passwordEncoder.matches(password, user.getPassword())){
                log.warn("Intento de login con contraseña incorrecta para usuario: {}", username);
                return LoginResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Usuario o contraseña incorrectos")
                    .build();
            }

            // Generar el token y añadirlo a la respuesta.
            String token = jwtService.generateToken(user.getId(), user.getUsername());
            
            log.info("Login exitoso para usuario: {}", username);
            
            return LoginResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Login exitoso")
                .setUserId(user.getId())
                .setToken(token)
                .build();
                
        } catch (Exception e) {
            log.error("Error crítico durante el login para usuario: {}", username, e);
            return LoginResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Error interno durante el login")
                .build();
        }
    }
}
