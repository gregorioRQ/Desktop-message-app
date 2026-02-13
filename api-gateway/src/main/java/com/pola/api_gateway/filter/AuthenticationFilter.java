package com.pola.api_gateway.filter;

import com.pola.api_gateway.util.JwtUtil;
import com.pola.api_gateway.config.RouteValidator;
import io.jsonwebtoken.ExpiredJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config>{

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Autowired
    private RouteValidator validator;

    @Autowired
    private JwtUtil jwtUtil;

    // Inyectamos Redis Reactivo para guardar la sesión sin bloquear
    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    // Tiempo de vida corto para detectar desconexiones (ej. 2 minutos)
    private static final Duration SESSION_TTL = Duration.ofMinutes(2);

    public AuthenticationFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(AuthenticationFilter.Config config) {
        return ((exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();

            // 1. Verificamos si la ruta requiere seguridad
            if (!validator.isSecured.test(request)) {
                logger.debug("Ruta pública, no requiere autenticación: {}", request.getURI().getPath());
                return chain.filter(exchange);
            }

            logger.debug("Ruta segura detectada: {}", request.getURI().getPath());

            // 2. Verificar existencia del header Authorization
            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return onError(exchange, "Falta header de autorización", HttpStatus.UNAUTHORIZED);
            }

            try {
                String token = extractToken(request);
                jwtUtil.validateToken(token);
                String userId = jwtUtil.extractUserId(token);

                logger.info("Token válido. Usuario: {}", userId);

                ServerHttpRequest modifiedRequest = updateRequest(request, userId);
                ServerWebExchange modifiedExchange = exchange.mutate().request(modifiedRequest).build();

                // Enrutamiento de lógica específica
                if (isWebSocketEndpoint(request)) {
                    return handleWebSocketSession(modifiedExchange, chain, userId);
                }

                if (isLogoutEndpoint(request)) {
                    return handleLogout(modifiedExchange, chain, userId);
                }

                return chain.filter(modifiedExchange);

            } catch (ExpiredJwtException e) {
                cleanupSession(e.getClaims().getSubject());
                return onError(exchange, "Acceso denegado: Token expirado", HttpStatus.UNAUTHORIZED);
            } catch (Exception e) {
                logger.error("Error de autenticación: {}", e.getMessage());
                return onError(exchange, "Acceso denegado: Token inválido", HttpStatus.UNAUTHORIZED);
            }
        });
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION).get(0);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Formato de token inválido");
    }

    private ServerHttpRequest updateRequest(ServerHttpRequest request, String userId) {
        return request.mutate()
                .header("X-User-Id", userId)
                .build();
    }

    private boolean isWebSocketEndpoint(ServerHttpRequest request) {
        return request.getURI().getPath().contains("/ws-binary");
    }

    private boolean isLogoutEndpoint(ServerHttpRequest request) {
        return request.getURI().getPath().contains("/logout");
    }

    private Mono<Void> handleWebSocketSession(ServerWebExchange exchange, GatewayFilterChain chain, String userId) {
        String sessionId = UUID.randomUUID().toString();
        String redisKey = "ws:sessionid:" + userId;

        logger.info("Guardando sesión WebSocket en Redis - Usuario: {}, SessionId: {}", userId, sessionId);

        return redisTemplate.opsForValue()
                .set(redisKey, sessionId, SESSION_TTL)
                .doOnError(error -> logger.error("Error al guardar en Redis: {}", error.getMessage()))
                .flatMap(success -> chain.filter(exchange));
    }

    private Mono<Void> handleLogout(ServerWebExchange exchange, GatewayFilterChain chain, String userId) {
        String redisKey = "ws:sessionid:" + userId;
        logger.info("Logout detectado. Eliminando sesión Redis para usuario: {}", userId);
        return redisTemplate.delete(redisKey)
                .flatMap(count -> chain.filter(exchange));
    }

    private void cleanupSession(String userId) {
        if (userId != null) {
            logger.warn("Limpiando sesión Redis por expiración para usuario: {}", userId);
            redisTemplate.delete("ws:sessionid:" + userId).subscribe();
        }
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        exchange.getResponse().setStatusCode(httpStatus);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // Configuración vacía
    }


}
