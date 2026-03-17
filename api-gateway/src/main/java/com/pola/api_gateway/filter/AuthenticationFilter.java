package com.pola.api_gateway.filter;

import com.pola.api_gateway.util.JwtUtil;
import com.pola.api_gateway.config.RouteValidator;
import io.jsonwebtoken.ExpiredJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;



@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config>{

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    @Autowired
    private RouteValidator validator;

    @Autowired
    private JwtUtil jwtUtil;

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
                String userId = jwtUtil.extractUserId(token);

                logger.info("Token válido. Usuario: {}", userId);

                return chain.filter(exchange);

            } catch (ExpiredJwtException e) {
                return onError(exchange, "Acceso denegado: Token expirado", HttpStatus.UNAUTHORIZED);
            } catch (Exception e) {
                logger.error("Error de autenticación: {}", e.getMessage());
                return onError(exchange, "Acceso denegado: Token inválido", HttpStatus.UNAUTHORIZED);
            }
        });
    }

    private String extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Formato de token inválido");
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        exchange.getResponse().setStatusCode(httpStatus);
        return exchange.getResponse().setComplete();
    }

    public static class Config {
        // Configuración vacía
    }


}
