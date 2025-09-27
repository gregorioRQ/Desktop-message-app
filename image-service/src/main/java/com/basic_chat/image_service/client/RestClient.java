package com.basic_chat.image_service.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class RestClient {
    private final RestTemplate restTemplate;
    private final static String AUTH_SERVICE_URL = "http://localhost:8083";

    public RestClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean verificarUsuario(Long userId) {
        String url = AUTH_SERVICE_URL + "/auth/" + userId;
        Boolean usuarioExiste = restTemplate.getForObject(url, Boolean.class);
        return usuarioExiste != null ? usuarioExiste : false;
    }
}
