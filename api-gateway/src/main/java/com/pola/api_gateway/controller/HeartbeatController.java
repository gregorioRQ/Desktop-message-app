package com.pola.api_gateway.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class HeartbeatController {

    @Autowired
    private ReactiveRedisTemplate<String, String> redisTemplate;

    // Debe coincidir con el TTL definido en AuthenticationFilter
    private static final Duration SESSION_TTL = Duration.ofMinutes(2);

    @GetMapping("/heartbeat")
    public Mono<ResponseEntity<String>> heartbeat(@RequestHeader("X-User-Id") String userId) {
        String redisKey = "ws:sessionid:" + userId;

        // Intentamos renovar el tiempo de vida de la clave
        return redisTemplate.expire(redisKey, SESSION_TTL)
                .map(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        // Si existe y se renovó: 200 OK
                        return ResponseEntity.ok("Alive");
                    } else {
                        // Si no existe la clave (ya expiró o nunca se conectó): 401 Unauthorized
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Session expired or not found");
                    }
                });
    }
}
