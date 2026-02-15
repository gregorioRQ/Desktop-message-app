package com.pola.api_gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;

@Component
public class RedisHealthCheck {

    private static final Logger logger = LoggerFactory.getLogger(RedisHealthCheck.class);

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @PostConstruct
    public void checkRedisConnection() {
        logger.info("Intentando conectar con Redis...");
        
        redisTemplate.execute(connection -> {
            logger.info("Conexión exitosa con Redis establecida");
            return connection.ping();
        })
                .doOnNext(pong -> logger.info("Redis PING respondió: {}", pong))
                .doOnError(error -> logger.error("Error al conectar con Redis: {}", error.getMessage()))
                .subscribe(
                    unused -> logger.debug("Redis connection validated"),
                    error -> logger.error("Error en validación de Redis: {}", error.getMessage())
                );
    }
}
