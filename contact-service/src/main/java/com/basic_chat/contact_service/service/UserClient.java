package com.basic_chat.contact_service.service;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UserClient {
    private final RestTemplate restTemplate;

    public UserClient(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    public boolean getUserById(Long id) {
        String url = "http://localhost:8083/auth/" + id;
        try {
            // El endpoint devuelve false si el usuario NO existe, true si existe
            Boolean userNotExist = restTemplate.getForObject(url, Boolean.class);
            return userNotExist; // true si existe
        } catch (Exception e) {
            // Si ocurre un error, asumimos que el usuario no existe
            return false;
        }
    }
}
