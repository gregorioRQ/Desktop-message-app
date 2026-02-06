package com.basic_chat.profile_service.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.basic_chat.profile_service.exception.TokenExpiredException;
import com.basic_chat.profile_service.exception.TokenNotFounException;
import com.basic_chat.profile_service.models.RefreshToken;
import com.basic_chat.profile_service.models.TokenPair;
import com.basic_chat.profile_service.models.User;
import com.basic_chat.profile_service.repository.RefreshTokenRepository;
import com.basic_chat.profile_service.repository.UserRepository;
import com.basic_chat.proto.RefreshTokenMessage;
import com.basic_chat.proto.RefreshTokenMessage.RefreshResponse;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureAlgorithm;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;


@Service
@Slf4j
public class JwtService {
      // Clave secreta compartida entre profile-service y chat-service
    // En producción: usar variables de entorno o config server
    private static final String SECRET_KEY = "your-256-bit-secret-key-here-change-in-production";
    private static final long EXPIRATION_TIME = 86400000; // 24 horas
    private final SecretKey key;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public JwtService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository){
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }
    // Constructor para usar la clave desde la configuracion.
    /* 
    public JwtService(@Value("${jwt.secret}") String secretKey){
        if(secretKey.isEmpty()){
            // Usar clave por defecto si no está configurada.
            this.key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        }else{
            this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        }
    }*/
    
    // Genera un token
    public String generateToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_TIME);
        
        return Jwts.builder()
                .subject(user.getId())
                .claim("username", user.getUsername())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }
    
    // Validar el token
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Extrae el userId dddel token
    public String getUserIdFromToken(String token){
        Claims claims = validateToken(token);
        return claims.getSubject();
    }

    // Extrae el username del token
    public String getUsernameFromToken(String token){
        Claims claims = validateToken(token);
        return claims.get("username", String.class);
    }

    // Verifica si un toke ha expirado
    public boolean isTokenExpired(String token){
        try{
            Claims claims = validateToken(token);
            return claims.getExpiration().before(new Date());
        }catch (Exception e){
            return true;
        }
    }

    // Extrae el userId e claims ya parseados
    public String getUserId(Claims claims){
        return claims.getSubject();
    }

    // Extrae el username de claims ya parseados
    public String getUsername(Claims claims){
        return claims.get("username", String.class);
    }

    /**
     * Genera un refresh token y lo guarda en la db
     * @param user Datos del usuario para generar el token.
     * @param deviceId El id del dispositivo desde el cual el usuario envio los datos.
     * @return token El token generado.
    */
    public String generateRefreshToken(User user, String deviceId) {
        // Generar token único y aleatorio
        String token = UUID.randomUUID().toString().replace("-", "") +
                       UUID.randomUUID().toString().replace("-", "");
        
        // Crear entidad para guardar en BD
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(token);
        refreshToken.setUserId(user.getId());
        refreshToken.setDeviceId(deviceId);
        refreshToken.setExpiryDate(LocalDateTime.now().plusDays(30));
        refreshToken.setCreatedAt(LocalDateTime.now());
        
        // Guardar la db
        refreshTokenRepository.save(refreshToken);
        
        return token;
    }

    /**
     * Genera un nuevo access token.
     * @param refreshToken el token necesario para solicitar un nuevo access token.
     * @return RefreshTokenMessage.RefreshResponse La clase envoltorio con el token nuevo.
     * en caso de error esta clase lleva un mensaje con el error especifico.
     */
    public RefreshTokenMessage.RefreshResponse refreshAccessToken(String refreshToken) {
        try {
            // Validar refresh token
            RefreshToken storedToken = refreshTokenRepository.findByToken(refreshToken).orElseThrow(()-> new TokenNotFounException("Refresh token invalido o no encontrado"));
                
            if (storedToken.getExpiryDate().isBefore(LocalDateTime.now())) {
                throw new TokenExpiredException("El token ha expirado");
            }
            
            // Generar nuevo access token
            User user = userRepository.findById(storedToken.getUserId())
                .orElseThrow(()-> new EntityNotFoundException("Usuario no encontrado"));
            String newAccessToken = generateToken(user);
            
            return RefreshTokenMessage.RefreshResponse.newBuilder()
                .setAccessToken(newAccessToken)
                .setRefreshToken(refreshToken)
                .build();
        } catch (TokenNotFounException  e) {
            log.error("No se halló el token {}", e);
            return sendErrorResponse(e.getMessage());
        } catch(TokenExpiredException e){
            log.error("El token ha expirado {}", e);
            return sendErrorResponse(e.getMessage());
        }catch (EntityNotFoundException e){
            log.error("Usuario no encontrado {}", e);
            return sendErrorResponse(e.getMessage());
        }catch (Exception e){
            log.error("Error al procesar el refreshToken {}", e);
            return sendErrorResponse("No se pudo procesar el refreshToken");
        }
    }

    private RefreshResponse sendErrorResponse(String error){
        return RefreshTokenMessage.RefreshResponse.newBuilder()
                .setAccessToken("")
                .setRefreshToken("")
                .setErrorMsg(error)
                .build();
    }

}
