package com.basic_chat.image_service.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.basic_chat.image_service.model.Image;
import com.basic_chat.image_service.model.ImageProfileSavedEvent;
import com.basic_chat.image_service.repository.ImageRepository;

import jakarta.transaction.Transactional;

import com.basic_chat.image_service.client.RestClient;
import com.basic_chat.image_service.exception.ResourceNotFoundException;

@Service
public class ImageService {
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_FORMATS = Set.of("image/jpeg", "image/png", "image/gif");

    @Value("${file.upload.dir:${user.home}}")
    private String uploadDir;

    private final RestClient restClient;
    private final ImageRepository imageRepository;

    public ImageService(RestClient restClient, ImageRepository imageRepository) {
        this.restClient = restClient;
        this.imageRepository = imageRepository;
    }

    @Transactional
    public Image cargarImagen(MultipartFile imagen, Long userId) {
        if (!restClient.verificarUsuario(userId)) {
            throw new IllegalArgumentException("El usuario no existe");
        }

        if (imagen != null && !imagen.isEmpty()) {
            validarImagen(imagen);
            return guardarImagen(imagen, userId);
        } else {
            throw new IllegalArgumentException("La imagen no puede estar vacía");
        }
    }

    private void validarImagen(MultipartFile imagen) {
        if (imagen.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("El tamaño de la imagen no puede ser mayor a 5MB");
        }
        String contentType = imagen.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("El archivo debe ser una imagen");
        }
        if (!ALLOWED_FORMATS.contains(contentType)) {
            throw new IllegalArgumentException("Formato de imagen no permitido: " + contentType);
        }

    }

    public Image obtenerImagenPorNombreOriginal(String nombreOriginal) {
        return imageRepository.findByOriginalFileName(nombreOriginal)
                .orElseThrow(() -> {
                    System.out.println("Imagen no encontrada: " + nombreOriginal);
                    return new ResourceNotFoundException("Imagen no encontrada con nombre: " + nombreOriginal);
                });
    }

    private Image guardarImagen(MultipartFile imagen, Long userId) {
        try {
            // Sanitizar el nombre del archivo
            String nombreArchivo = imagen.getOriginalFilename();
            String nombreSanitizado = nombreArchivo.replaceAll("[^a-zA-Z0-9.\\-_]", "_");

            // nombre unico para el archivo
            String nombreUnico = System.currentTimeMillis() + "_" + nombreSanitizado;

            // construir la ruta absoluta del archivo
            Path directorioImagenes = Paths.get(uploadDir, "image-service");

            // Crear el directorio si no existe
            if (!directorioImagenes.toFile().exists()) {
                Files.createDirectories(directorioImagenes);
            }

            Path rutaCompleta = directorioImagenes.resolve(nombreUnico);
            Files.copy(imagen.getInputStream(), rutaCompleta, StandardCopyOption.REPLACE_EXISTING);

            // Crea el objeto Image
            Image image = new Image();
            image.setOriginalFileName(imagen.getOriginalFilename());
            image.setStoredFileName(nombreUnico);
            image.setUrl(uploadDir + nombreUnico); // URL relativa de acceso
            image.setContentType(imagen.getContentType());
            image.setUserId(userId);
            image.setSize(imagen.getSize());
            image.setUploadedAt(LocalDateTime.now());

            // Guarda en base de datos
            imageRepository.save(image);
            return image;
        } catch (Exception e) {
            throw new RuntimeException("Error al guardar la imagen: " + e.getMessage(), e);
        }
    }
}
