package com.basic_chat.chat_service.client;

import java.io.File;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
public class RestClient {
    private final RestTemplate restTemplate;
    private final String CONTACT_SERVICE_URL = "http://localhost:8082";
    private final String IMAGE_SERVICE_URL = "http://localhost:8086";

    public RestClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Consulta al servicio de contactos para verificar si un usuario está
     * bloqueado.
     *
     * @return true si el usuario está bloqueado, false en caso contrario.
     */
    public boolean isUserBlocked(Long userId, Long contactId) {
        String url = CONTACT_SERVICE_URL + "/contacts/is-blocked?userId=" + userId + "&contactId=" + contactId;
        Boolean result = restTemplate.getForObject(url, Boolean.class);
        return result != null ? result : false;
    }

    /**
     * Envía una imagen al servicio de imágenes.
     * 
     * @param to   El nombre de usuario del receptor.
     * @param file MultipartFile que contiene la imagen a enviar.
     * @return La respuesta del servicio de imágenes como String.
     */
    public String sendImage(MultipartFile file, String to) {
        System.out.println("Enviando imagen al servicio de imágenes: " + file.getOriginalFilename()
                + " para el usuario: " + to);
        String url = IMAGE_SERVICE_URL + "/image/upload";

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", file.getResource());
        body.add("username", to);

        return restTemplate.postForObject(url, body, String.class);
    }
}
