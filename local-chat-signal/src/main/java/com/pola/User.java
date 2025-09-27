package com.pola;

public class User {
    private String username;
    private byte[] publicKey;

    public User(String username, byte[] publicKey) {
        this.username = username;
        this.publicKey = publicKey;
    }

    public String getUsername() {
        return username;
    }

    public byte[] getPublicKey() {
        return publicKey;
    }
}
