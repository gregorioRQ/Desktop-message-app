package com.pola.model;

// Administra los datos de la sesion en memoria.
public class Session {
    private String accessToken;
    private String refreshToken;
    private String userId;
    private String username;
    private String deviceId;

    public Session(String accessToken, String refreshToken, String userId, String username, String deviceId) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.userId = userId;
        this.username = username;
        this.deviceId = deviceId;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getDeviceId() { return deviceId; }
}