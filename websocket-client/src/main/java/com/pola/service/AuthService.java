package com.pola.service;

import com.pola.model.Session;
import com.pola.proto.LoginProto.LoginRequest;
import com.pola.proto.LoginProto.LoginResponse;
import com.pola.proto.LogoutProto.LogoutRequest;
import com.pola.proto.LogoutProto.LogoutResponse;
import com.pola.proto.RefreshTokenMessage.RefreshRequest;
import com.pola.proto.RefreshTokenMessage.RefreshResponse;
import com.pola.repository.TokenRepository;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

//Este servicio orquesta el login, el auto-login y el refresco de tokens utilizando el nuevo .proto.

public class AuthService {
    private final HttpService httpService;
    private final TokenRepository tokenRepository;
    private final String deviceId;

    public AuthService(HttpService httpService) {
        this(httpService, new TokenRepository());
    }

    public AuthService(HttpService httpService, TokenRepository tokenRepository) {
        this.httpService = httpService;
        this.tokenRepository = tokenRepository;
        this.deviceId = getOrCreateDeviceId();
    }

    public CompletableFuture<Session> tryAutoLogin() {
        Session storedSession = tokenRepository.loadSession();
        if (storedSession == null) {
            return CompletableFuture.failedFuture(new Exception("No hay sesión guardada"));
        }

        // Intentar refrescar el token inmediatamente para asegurar validez y obtener uno nuevo
        return refreshSession(storedSession);
    }

    public CompletableFuture<Session> login(String username, String password) {
        LoginRequest request = LoginRequest.newBuilder()
                .setUsername(username)
                .setPassword(password)
                .setDeviceId(deviceId)
                .build();

        return httpService.login(request, LoginResponse.class)
                .thenApply(response -> {
                    if (response.getSuccess()) {
                        Session session = new Session(
                            response.getTokens().getAccessToken(),
                            response.getTokens().getRefreshToken(),
                            response.getUserId(),
                            username,
                            deviceId
                        );
                        tokenRepository.saveSession(session);
                        return session;
                    } else {
                        throw new RuntimeException(response.getMessage());
                    }
                });
    }

    public void logout() {
        tokenRepository.clearSession();
    }

    public CompletableFuture<LogoutResponse> logout(String refreshToken) {
        String accessToken = "";
        Session session = tokenRepository.loadSession();
        if (session != null) {
            accessToken = session.getAccessToken();
        }

        LogoutRequest request = LogoutRequest.newBuilder()
                .setRefreshToken(refreshToken)
                .build();

        return httpService.logout(request, accessToken, LogoutResponse.class);
    }


    private CompletableFuture<Session> refreshSession(Session currentSession) {
        RefreshRequest request = RefreshRequest.newBuilder()
                .setToken(currentSession.getRefreshToken())
                .build();

        return httpService.refreshToken(request, RefreshResponse.class)
                .thenApply(response -> {
                    if (response.getAccessToken() != null && !response.getAccessToken().isEmpty()) {
                        Session newSession = new Session(
                            response.getAccessToken(),
                            response.getRefreshToken(),
                            currentSession.getUserId(),
                            currentSession.getUsername(),
                            deviceId
                        );
                        tokenRepository.saveSession(newSession);
                        return newSession;
                    } else {
                        tokenRepository.clearSession();
                        throw new RuntimeException("Refresh failed: " + response.getErrorMsg());
                    }
                });
    }

    private String getOrCreateDeviceId() {
        try {
            File deviceFile = new File("device.id");
            if (deviceFile.exists()) {
                return Files.readString(deviceFile.toPath()).trim();
            } else {
                String newId = UUID.randomUUID().toString();
                Files.writeString(deviceFile.toPath(), newId);
                return newId;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return UUID.randomUUID().toString();
        }
    }
}