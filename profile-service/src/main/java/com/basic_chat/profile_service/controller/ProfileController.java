package com.basic_chat.profile_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.basic_chat.profile_service.models.Profile;
import com.basic_chat.profile_service.service.ProfileService;

import jakarta.persistence.EntityNotFoundException;

@RestController
@RequestMapping("/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping("/create")
    public ResponseEntity<Profile> createProfile(@RequestBody Profile profile) {
        if (profile == null) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        try {
            Profile createdProfile = profileService.createProfile(profile);
            return new ResponseEntity<>(createdProfile, HttpStatus.CREATED);

        } catch (EntityNotFoundException ex) {
            System.out.println(ex.getMessage());
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }

    @GetMapping("/get-by-id")
    public ResponseEntity<Profile> getProfileById(@RequestParam Long userId) {
        try {
            if (userId == null) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            Profile profile = profileService.getProfileById(userId);
            return new ResponseEntity<>(profile, HttpStatus.OK);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            ex.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }

    }
}
