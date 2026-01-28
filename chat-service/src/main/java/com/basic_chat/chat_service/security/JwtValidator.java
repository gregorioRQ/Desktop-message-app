package com.basic_chat.chat_service.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class JwtValidator {
    // IMPORTANTE: Debe ser la MISMA clave que en profile-service
    private static final String SECRET_KEY = "your-256-bit-secret-key-here-change-in-production";
    
    private final SecretKey key;
    
    public JwtValidator() {
        this.key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
    }
    
    /**
     * Constructor alternativo para usar clave desde configuración
     
    public JwtValidator(@Value("${jwt.secret:}") String secretKey) {
        if (secretKey.isEmpty()) {
            this.key = Keys.hmacShaKeyFor(SECRET_KEY.getBytes(StandardCharsets.UTF_8));
        } else {
            this.key = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
        }
    }*/
    
    /**
     * Valida un token JWT y retorna los claims
     * 
     * @param token Token JWT a validar
     * @return Claims del token
     * @throws JwtException si el token es inválido o ha expirado
     */
    public Claims validateToken(String token) throws JwtException {
        if(token == null){
            log.warn("No se pudo validar el token");
            throw new IllegalArgumentException("Token de autenticacion nulo");
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            throw e;
        }
    }
    
    /**
     * Extrae el userId del token
     * 
     * @param claims Claims del token
     * @return User ID
     */
    public String getUserId(Claims claims) {
        return claims.getSubject();
    }
    
    /**
     * Extrae el username del token
     * 
     * @param claims Claims del token
     * @return Username
     */
    public String getUsername(Claims claims) {
        return claims.get("username", String.class);
    }
    
    /**
     * Valida y extrae el userId en un solo paso
     * 
     * @param token Token JWT
     * @return User ID
     * @throws JwtException si el token es inválido
     */
    public String getUserIdFromToken(String token) throws JwtException {
        Claims claims = validateToken(token);
        return getUserId(claims);
    }
    
    /**
     * Valida y extrae el username en un solo paso
     * 
     * @param token Token JWT
     * @return Username
     * @throws JwtException si el token es inválido
     */
    public String getUsernameFromToken(String token) throws JwtException {
        Claims claims = validateToken(token);
        return getUsername(claims);
    }
}
