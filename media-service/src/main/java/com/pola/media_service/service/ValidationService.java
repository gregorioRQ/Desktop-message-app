package com.pola.media_service.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.stereotype.Service;

import com.pola.media_service.config.MediaServiceProperties;
import com.pola.media_service.exception.ValidationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.pola.media_service.constants.MediaConstants.*;

/**
 * Servicio de validación para imágenes y parámetros de negocio.
 * Centraliza todas las validaciones para cumplir con Single Responsibility Principle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationService {

    private final MediaServiceProperties properties;

    /**
     * Valida que los datos de la imagen cumplan con todas las reglas de negocio.
     * 
     * @param imageData Datos binarios de la imagen
     * @param userId ID del usuario que sube la imagen
     * @param receiverId ID del usuario que recibirá la imagen
     * @throws ValidationException si alguna validación falla
     */
    public void validateImageUpload(byte[] imageData, String userId, String receiverId) {
        log.debug("Validating image upload: userId={}, receiverId={}, size={}KB", 
            userId, receiverId, imageData.length / BYTES_IN_KB);
        
        validateNotNull(imageData, "Image data cannot be null");
        validateNotNull(userId, "User ID cannot be null");
        validateNotNull(receiverId, "Receiver ID cannot be null");
        validateNotEmpty(userId, "User ID cannot be empty");
        validateNotEmpty(receiverId, "Receiver ID cannot be empty");
        
        validateImageSize(imageData);
        validateImageFormat(imageData);
        validateImageNotCorrupted(imageData);
        
        log.debug("Image validation passed: userId={}", userId);
    }

    /**
     * Valida que un objeto no sea null.
     * 
     * @param object Objeto a validar
     * @param message Mensaje de error si es null
     * @throws ValidationException si el objeto es null
     */
    private void validateNotNull(Object object, String message) {
        if (object == null) {
            log.error("Validation failed: {}", message);
            throw new ValidationException(message);
        }
    }

     /**
     * Valida que un String no sea vacío.
     */
    private void validateNotEmpty(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            log.error("Validation failed: {}", message);
            throw new ValidationException(message);
        }
    }
    
    /**
     * Valida que el tamaño de la imagen no exceda el límite configurado.
     * 
     * @param imageData Datos de la imagen
     * @throws ValidationException si excede el tamaño máximo
     */
    private void validateImageSize(byte[] imageData) {
        long maxSizeBytes = properties.getStorage().getMaxSizeMb() * BYTES_IN_MB;
        
        if (imageData.length > maxSizeBytes) {
            String errorMsg = String.format(ERROR_IMAGE_TOO_LARGE, 
                properties.getStorage().getMaxSizeMb());
            log.error("Image too large: size={}MB, max={}MB", 
                imageData.length / BYTES_IN_MB, 
                properties.getStorage().getMaxSizeMb());
            throw new ValidationException(errorMsg);
        }
        
        log.debug("Image size validation passed: {}KB", imageData.length / BYTES_IN_KB);
    }

    /**
     * Valida que el formato de la imagen sea uno de los permitidos.
     * 
     * @param imageData Datos de la imagen
     * @throws ValidationException si el formato no es válido
     */
    private void validateImageFormat(byte[] imageData) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(
                new ByteArrayInputStream(imageData))) {
            
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            
            if (!readers.hasNext()) {
                log.error("No image reader found for provided data");
                throw new ValidationException(String.format(ERROR_INVALID_FORMAT, 
                    Arrays.toString(properties.getProcessing().getAllowedFormats())));
            }
            
            ImageReader reader = readers.next();
            String formatName = reader.getFormatName().toLowerCase();
            reader.dispose();
            
            boolean isValidFormat = Arrays.asList(properties.getProcessing().getAllowedFormats())
                .contains(formatName);
            
            if (!isValidFormat) {
                log.error("Invalid image format: {}", formatName);
                throw new ValidationException(String.format(ERROR_INVALID_FORMAT, 
                    Arrays.toString(properties.getProcessing().getAllowedFormats())));
            }
            
            log.debug("Image format validation passed: {}", formatName);
            
        } catch (IOException e) {
            log.error("Error validating image format", e);
            throw new ValidationException(ERROR_CORRUPTED_IMAGE);
        }
    }

    /**
     * Valida que la imagen no esté corrupta intentando leerla.
     * 
     * @param imageData Datos de la imagen
     * @throws ValidationException si la imagen está corrupta
     */
    private void validateImageNotCorrupted(byte[] imageData) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(imageData));
            
            if (image == null) {
                log.error("Image is corrupted or unreadable");
                throw new ValidationException(ERROR_CORRUPTED_IMAGE);
            }
            
            log.debug("Image corruption check passed: {}x{}", 
                image.getWidth(), image.getHeight());
            
        } catch (IOException e) {
            log.error("Error reading image data", e);
            throw new ValidationException(ERROR_CORRUPTED_IMAGE);
        }
    }

    /**
     * Valida que el usuario tenga permisos para descargar un media.
     * 
     * @param requestUserId ID del usuario que solicita
     * @param senderId ID del emisor del media
     * @param receiverId ID del receptor del media
     * @throws ValidationException si no tiene permisos
     */
    public void validateDownloadPermission(String requestUserId, String senderId, String receiverId) {
        log.debug("Validating download permission: requestUserId={}, senderId={}, receiverId={}", 
            requestUserId, senderId, receiverId);
        
        boolean hasPermission = requestUserId.equals(senderId) || requestUserId.equals(receiverId);
        
        if (!hasPermission) {
            log.warn("Unauthorized download attempt: userId={}, mediaOwnedBy={} or {}", 
                requestUserId, senderId, receiverId);
            throw new ValidationException(ERROR_UNAUTHORIZED);
        }
        
        log.debug("Download permission granted: userId={}", requestUserId);
    }

}
