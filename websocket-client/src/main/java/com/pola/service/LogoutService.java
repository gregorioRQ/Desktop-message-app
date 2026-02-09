package com.pola.service;

import com.pola.proto.LogoutProto.LogoutRequest;
import com.pola.proto.LogoutProto.LogoutResponse;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class LogoutService {
    // URL del endpoint de logout - Ajustar puerto/ruta según la configuración del backend
    private static final String LOGOUT_URL = "http://localhost:8080/api/auth/logout";
    private final HttpClient httpClient;

    public LogoutService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public CompletableFuture<LogoutResponse> logout(String refreshToken) {
        LogoutRequest request = LogoutRequest.newBuilder()
                .setRefreshToken(refreshToken)
                .build();

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(LOGOUT_URL))
                .header("Content-Type", "application/x-protobuf")
                .POST(HttpRequest.BodyPublishers.ofByteArray(request.toByteArray()))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    try {
                        if (response.statusCode() == 200) {
                            return LogoutResponse.parseFrom(response.body());
                        } else {
                            return LogoutResponse.newBuilder()
                                    .setSuccess(false)
                                    .setMessage("Error HTTP: " + response.statusCode())
                                    .build();
                        }
                    } catch (Exception e) {
                        return LogoutResponse.newBuilder()
                                .setSuccess(false)
                                .setMessage("Error de procesamiento: " + e.getMessage())
                                .build();
                    }
                });
    }
}