package com.basic_chat.profile_service.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureAlgorithm;


@Service
public class JwtService {
      // Clave secreta compartida entre profile-service y chat-service
    // En producción: usar variables de entorno o config server
    private static final String SECRET_KEY = "your-256-bit-secret-key-here-change-in-production";
    private static final long EXPIRATION_TIME = 86400000; // 24 horas
    private final SecretKey key;

    public JwtService(){
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
    public String generateToken(String userId, String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_TIME);
        
        return Jwts.builder()
                .subject(userId)
                .claim("username", username)
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

}
