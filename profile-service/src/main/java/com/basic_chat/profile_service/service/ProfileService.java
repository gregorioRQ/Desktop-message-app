package com.basic_chat.profile_service.service;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.basic_chat.profile_service.models.ImageProfileSavedEvent;
import com.basic_chat.profile_service.models.Profile;
import com.basic_chat.profile_service.repository.ProfileRepository;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;

@Service
public class ProfileService {
    private final ProfileRepository profileRepository;
    private final RestTemplate restTemplate;
    private final String authServiceUrl = "http://localhost:8083";

    public ProfileService(ProfileRepository profileRepository, RestTemplate restTemplate) {
        this.profileRepository = profileRepository;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public Profile createProfile(Profile profile) {

        String url = authServiceUrl + "/auth/" + profile.getUserId();
        Boolean usuarioExiste = restTemplate.getForObject(url, Boolean.class);
        if (usuarioExiste == null || !usuarioExiste) {
            throw new EntityNotFoundException("Usuario no encontrado");
        }

        Profile savedProfile = new Profile();
        // hacer una llamada al auth-service para verificar si existe el usuario
        savedProfile.setUserId(profile.getUserId());
        savedProfile.setProfileName(profile.getProfileName());
        savedProfile.setBio(profile.getBio());
        savedProfile.setDateOfBirth(profile.getDateOfBirth());
        System.out.println(savedProfile.toString());
        // IMPLEMENTAR LA LOGICA PARA EL MANEJO DE FAVORITOS

        return profileRepository.save(profile);

    }

    public Profile getProfileById(Long userId) {
        return profileRepository.findByUserId(userId);
    }

    // escuchar el evento de imagen de perfil guardada
    @RabbitListener(queues = "image.profile.saved")
    public String handleImageProfileSavedEvent(ImageProfileSavedEvent event) {
        // Lógica para manejar el evento de imagen de perfil guardada

        // buscar el perfil en la base de datos segun el userId
        Profile profile = profileRepository.findByUserId(event.getUserId());
        if (profile == null) {
            throw new EntityNotFoundException("No hay un perfil con este userId vinculado");
        }
        profile.setProfilePictureUrl(event.getUrl());
        System.out.println("URL A LA FOTO DDE PERFIL: " + profile.getProfilePictureUrl());

        return event.getUrl();
    }
}
