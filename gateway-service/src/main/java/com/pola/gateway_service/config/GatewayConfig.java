package com.pola.gateway_service.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    private static final Logger logger = LoggerFactory.getLogger(GatewayConfig.class);

    @Bean
    public RouteLocator routes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("auth-service", r -> r.path("/auth/**")
                        .filters(f -> f.filter((exchange, chain) -> {
                            logger.info("🔐 Pasando por el gateway → auth-service");
                            return chain.filter(exchange);
                        }))
                        .uri("http://localhost:8081"))
                .route("profile-service", r -> r.path("/profiles/**")
                        .filters(f -> f.filter((exchange, chain) -> {
                            logger.info("👤 Pasando por el gateway → profile-service");
                            return chain.filter(exchange);
                        }))
                        .uri("http://localhost:8082"))
                .build();
    }
}
