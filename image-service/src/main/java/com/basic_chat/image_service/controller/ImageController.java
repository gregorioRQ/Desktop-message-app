package com.basic_chat.image_service.controller;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.basic_chat.image_service.model.Image;
import com.basic_chat.image_service.model.ImageProfileSavedEvent;
import com.basic_chat.image_service.service.ImageService;

@RestController
@RequestMapping("/image")
public class ImageController {
    private final ImageService imageService;
    private final RabbitTemplate rabbitTemplate;

    public ImageController(ImageService imageService, RabbitTemplate rabbitTemplate) {
        this.imageService = imageService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId) {
        try {
            Image image = imageService.cargarImagen(file, userId);
            return ResponseEntity.ok(image);
        } catch (IllegalArgumentException ex) {
            System.out.println(ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        }

    }

    @PostMapping("/upload-profile")
    public ResponseEntity<?> uploadImageProfile(@RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId) {

        try {

            Image image = imageService.cargarImagen(file, userId);
            // publicar el evento de imagen de perfil guardada
            ImageProfileSavedEvent event = new ImageProfileSavedEvent(
                    image.getUrl(),
                    image.getOriginalFileName(),
                    image.getContentType(),
                    image.getSize(),
                    userId,
                    image.getStoredFileName(),
                    image.getUploadedAt());
            rabbitTemplate.convertAndSend("image.profile.saved", event);
            return ResponseEntity.ok(image);
        } catch (IllegalArgumentException ex) {
            System.out.println(ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        }

    }

    @GetMapping("/by-name/{fileName}")
    public ResponseEntity<Image> getImageByOriginalName(@PathVariable String fileName) {
        Image image = imageService.obtenerImagenPorNombreOriginal(fileName);
        return ResponseEntity.ok(image);
    }
}
