package com.basic_chat.profile_service.service;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import com.basic_chat.profile_service.config.UserCreatedRabbitConfig;
import com.basic_chat.profile_service.config.UserLogoutRabbitConfig;
import com.basic_chat.profile_service.dto.UserCreatedEvent;
import com.basic_chat.profile_service.models.User;
import com.basic_chat.profile_service.repository.UserRepository;
import com.basic_chat.proto.LoginProto.LoginRequest;
import com.basic_chat.proto.LoginProto.LoginResponse;
import com.basic_chat.proto.LoginProto.TokenPair;
import com.basic_chat.proto.LogoutProto.LogoutRequest;
import com.basic_chat.proto.LogoutProto.LogoutResponse;
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
    private final RabbitTemplate rabbitTemplate;

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

            // Publicar evento para notification-service
            publishUserCreatedEvent(savedUser.getId(), username);
            
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
        String deviceId = request.getDeviceId();
        
        log.info("Iniciando login para usuario: {}", username);
       

        try {
            Optional<User> userOpt = authenticate(username, password);

            if (userOpt.isEmpty()) {
                return LoginResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("Usuario o contraseña incorrectos")
                    .build();
            }

            User user = userOpt.get();
                // Generar los tokens
                String accessToken = jwtService.generateToken(user);
                String refreshToken = jwtService.generateRefreshToken(user, deviceId);
        
                TokenPair tokens = TokenPair.newBuilder()
                        .setAccessToken(accessToken)
                        .setRefreshToken(refreshToken)
                        .build();
          
            
            log.info("Login exitoso para usuario: {}", username);
            
            return LoginResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Login exitoso")
                .setUserId(user.getId())
                .setTokens(tokens)
                .build();
                
        } catch (Exception e) {
            log.error("Error crítico durante el login para usuario: {}", username, e);
            return LoginResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Error interno durante el login")
                .build();
        }
    }

    private Optional<User> authenticate(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            log.warn("Intento de login con usuario inexistente: {}", username);
            return Optional.empty();
        }

        User user = userOpt.get();
        if (!passwordEncoder.matches(password, user.getPassword())) {
            log.warn("Intento de login con contraseña incorrecta para usuario: {}", username);
            return Optional.empty();
        }
        return Optional.of(user);
    }

    /**
     * Procesa el cierre de sesión del usuario.
     * 
     * Este método:
     * 1. Valida que el refreshToken no esté vacío
     * 2. Elimina el refreshToken de la base de datos
     * 3. Publica evento de logout para que connection-service elimine la clave Redis
     * 
     * @param request Solicitud de logout con refreshToken y username
     * @return LogoutResponse con estado de la operación
     */
    @Override
    @Transactional
    public LogoutResponse logout(LogoutRequest request) {
        String refreshToken = request.getRefreshToken();
        String username = request.getUsername();
        
        if (refreshToken == null || refreshToken.isEmpty()) {
            log.warn("Parámetros inválidos, refreshToken vacío");
            return LogoutResponse.newBuilder()
                .setMessage("Token no enviado")
                .setSuccess(false)
                .build();
        }

        if (username == null || username.isEmpty()) {
            log.warn("Parámetros inválidos, username vacío en logout");
            return LogoutResponse.newBuilder()
                .setMessage("Username no enviado")
                .setSuccess(false)
                .build();
        }

        try {
            jwtService.deleteRefreshToken(refreshToken);
            log.info("RefreshToken eliminado para usuario: {}", username);

            publishUserLogoutEvent(username);
            
            log.info("Logout completado para usuario: {}. Evento publicado a connection-service", username);
            
            return LogoutResponse.newBuilder()
                .setMessage("Logout exitoso")
                .setSuccess(true)
                .setRedisKeyDeleted(true)
                .build();
                
        } catch (Exception ex) {
            log.error("Error durante logout para usuario {}: {}", username, ex.getMessage());
            return LogoutResponse.newBuilder()
                .setMessage("Error durante el logout")
                .setSuccess(false)
                .build();
        }
    }

    /**
     * Publica un evento de logout a RabbitMQ para que connection-service elimine
     * la clave Redis user:name:{username}.
     * 
     * Este método envía el username a la cola 'user.logout' donde connection-service
     * está escuchando para procesar la eliminación de la clave de Redis.
     * 
     * @param username Nombre del usuario que ha cerrado sesión
     */
    private void publishUserLogoutEvent(String username) {
        try {
            rabbitTemplate.convertAndSend(
                UserLogoutRabbitConfig.USER_LOGOUT_EXCHANGE,
                UserLogoutRabbitConfig.USER_LOGOUT_ROUTING_KEY,
                username
            );
            log.info("Evento de logout publicado para usuario: {}", username);
        } catch (Exception e) {
            log.error("Error al publicar evento de logout para usuario {}: {}", username, e.getMessage());
            throw e;
        }
    }

    /**
     * Publica un evento de usuario creado a RabbitMQ para que notification-service
     * cree el registro en su tabla de usuarios.
     * 
     * Este método envía un evento con userId y username a la cola 'user.created' 
     * donde notification-service está escuchando para crear el usuario en su base de datos.
     * 
     * @param userId ID del usuario creado en profile-service
     * @param username Nombre del usuario creado
     */
    private void publishUserCreatedEvent(String userId, String username) {
        try {
            UserCreatedEvent event = new UserCreatedEvent(userId, username);
            rabbitTemplate.convertAndSend(
                UserLogoutRabbitConfig.USER_LOGOUT_EXCHANGE,
                UserCreatedRabbitConfig.USER_CREATED_ROUTING_KEY,
                event
            );
            log.info("Evento de usuario creado publicado para userId: {}, username: {}", userId, username);
        } catch (Exception e) {
            log.error("Error al publicar evento de usuario creado para userId {}: {}", userId, e.getMessage());
            throw e;
        }
    }
}
