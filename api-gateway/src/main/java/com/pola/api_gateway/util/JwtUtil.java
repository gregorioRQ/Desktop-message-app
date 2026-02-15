package com.pola.api_gateway.util;

import org.springframework.stereotype.Component;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;

@Component
public class JwtUtil {
    // CAMBIAR: Esta clave debe ser la misma que usa profile-service para firmar.
    // Debe ser Base64 y de al menos 256 bits.
    public static final String SECRET = "w8p3uP3Kz7m2+uFq7y8Zx9cD0y1WkX9KZk3M0FJH8qE=";

    public void validateToken(final String token) {
        // Jwts.parserBuilder() maneja internamente la decodificación Base64Url del token.
        // No es necesario (y es incorrecto) decodificar el token manualmente antes de pasarlo aquí.
        Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
    }

    // Extraemos el ID del usuario para guardarlo en Redis o pasarlo en headers
    public String extractUserId(String token) {
        return Jwts.parserBuilder().setSigningKey(getSigningKey()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    private Key getSigningKey() {
        // USAR ESTO: Decoders.BASE64 de io.jsonwebtoken
        // Esto asegura compatibilidad total con la librería y evita errores con java.util.Base64
        byte[] keyBytes = Decoders.BASE64.decode(SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

