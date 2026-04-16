package com.pola.media_service.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

import org.springframework.stereotype.Service;

import com.pola.media_service.config.MediaServiceProperties;
import com.pola.media_service.exception.StorageException;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.pola.media_service.constants.MediaConstants.*;

/**
 * Servicio de almacenamiento de archivos en disco.
 * Gestiona la escritura, lectura y eliminación de archivos de medios.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final MediaServiceProperties properties;
    private Path baseStoragePath;

    /**
     * Inicializa el servicio creando el directorio base si no existe.
     */
    @PostConstruct
    public void init() {
        try {
            baseStoragePath = Paths.get(properties.getStorage().getBasePath());
            
            if (properties.getStorage().isAutoCreateDirectories()) {
                Files.createDirectories(baseStoragePath);
                log.info("Storage initialized: path={}", baseStoragePath.toAbsolutePath());
            } else if (!Files.exists(baseStoragePath)) {
                log.error("Storage path does not exist and auto-create is disabled: {}", 
                    baseStoragePath);
                throw new StorageException("Storage path does not exist: " + baseStoragePath);
            }
            
        } catch (IOException e) {
            log.error("Failed to initialize storage", e);
            throw new StorageException("Failed to initialize storage", e);
        }
    }
    
     /**
     * Almacena la imagen completa en disco.
     * 
     * @param mediaId ID único del media
     * @param userId ID del usuario (para organizar en carpetas)
     * @param fullImageData Datos de la imagen completa
     * @return StorageResult con las rutas de los archivos guardados
     * @throws StorageException si falla el almacenamiento
     */
    public StorageResult storeMedia(String mediaId, String userId, byte[] fullImageData) {
        log.info("Storing media: mediaId={}, userId={}, fullSize={}KB",
            mediaId, userId, 
            fullImageData.length / BYTES_IN_KB);
        
        try {
            Path userDirectory = createUserDirectory(userId);
            
            String fullImageFilename = mediaId + SUFFIX_FULL + EXTENSION_WEBP;
            Path fullImagePath = userDirectory.resolve(fullImageFilename);
            Files.write(fullImagePath, fullImageData, StandardOpenOption.CREATE);
            log.debug("Full image stored: path={}", fullImagePath);
            
            log.info("Media stored successfully: mediaId={}", mediaId);
            
            return new StorageResult(fullImagePath.toString());
            
        } catch (IOException e) {
            log.error("Failed to store media: mediaId={}", mediaId, e);
            throw new StorageException(ERROR_STORAGE_FAILED, e);
        }
    }

    /**
     * Crea el directorio de un usuario si no existe.
     * 
     * @param userId ID del usuario
     * @return Path del directorio creado
     * @throws IOException si falla la creación
     */
    private Path createUserDirectory(String userId) throws IOException {
        Path userDir = baseStoragePath.resolve("user_" + userId);
        
        if (!Files.exists(userDir)) {
            Files.createDirectories(userDir);
            log.debug("User directory created: {}", userDir);
        }
        
        return userDir;
    }

     /**
     * Lee un archivo de medios desde disco.
     * 
     * @param filePath Ruta completa del archivo
     * @return Datos del archivo
     * @throws StorageException si el archivo no existe o falla la lectura
     */
    public byte[] readFile(String filePath) {
        log.debug("Reading file: path={}", filePath);
        
        try {
            Path path = Paths.get(filePath);
            
            if (!Files.exists(path)) {
                log.error("File not found: {}", filePath);
                throw new StorageException("File not found: " + filePath);
            }
            
            byte[] data = Files.readAllBytes(path);
            log.debug("File read successfully: path={}, size={}KB", 
                filePath, data.length / BYTES_IN_KB);
            
            return data;
            
        } catch (IOException e) {
            log.error("Failed to read file: path={}", filePath, e);
            throw new StorageException("Failed to read file: " + filePath, e);
        }
    }

    /**
     * Elimina un media del disco.
     * 
     * @param fullImagePath Ruta de la imagen completa
     * @throws StorageException si falla la eliminación
     */
    public void deleteMedia(String fullImagePath) {
        log.info("Deleting media: fullImagePath={}", fullImagePath);
        
        boolean fullImageDeleted = deleteFile(fullImagePath);
        
        if (fullImageDeleted) {
            log.info("Media deleted successfully");
        } else {
            log.warn("Media deletion incomplete: fullImage={}", fullImageDeleted);
        }
    }

    /**
     * Elimina un archivo individual del disco.
     * 
     * @param filePath Ruta del archivo
     * @return true si se eliminó exitosamente
     */
    private boolean deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            
            if (Files.exists(path)) {
                Files.delete(path);
                log.debug("File deleted: {}", filePath);
                return true;
            } else {
                log.warn("File does not exist, skipping deletion: {}", filePath);
                return false;
            }
            
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath, e);
            return false;
        }
    }

    /**
     * Verifica si un archivo existe en disco.
     * 
     * @param filePath Ruta del archivo
     * @return true si existe
     */
    public boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }

    /**
     * Obtiene el tamaño de un archivo en bytes.
     * 
     * @param filePath Ruta del archivo
     * @return Tamaño en bytes
     * @throws StorageException si falla la operación
     */
    public long getFileSize(String filePath) {
        try {
            return Files.size(Paths.get(filePath));
        } catch (IOException e) {
            log.error("Failed to get file size: {}", filePath, e);
            throw new StorageException("Failed to get file size", e);
        }
    }
    
    /**
     * Clase interna para resultado de almacenamiento.
     */
    public record StorageResult(String fullImagePath) {}

}
