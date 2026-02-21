package com.pola.media_service.constants;

/**
 * Constantes utilizadas en el servicio de medios.
 * Centraliza valores mágicos y evita hardcoding.
 */
public final class MediaConstants {
    
    // Prevenir instanciación
    private MediaConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
    
    // Formatos de imagen
    public static final String FORMAT_WEBP = "webp";
    public static final String FORMAT_JPEG = "jpg";
    public static final String FORMAT_PNG = "png";
    
    // MIME types
    public static final String MIME_TYPE_WEBP = "image/webp";
    public static final String MIME_TYPE_JPEG = "image/jpeg";
    public static final String MIME_TYPE_PNG = "image/png";
    
    // Sufijos de archivos
    public static final String SUFFIX_THUMBNAIL = "_thumb";
    public static final String SUFFIX_FULL = "_full";
    
    // Extensiones de archivos
    public static final String EXTENSION_WEBP = ".webp";
    
    // Tamaños
    public static final long BYTES_IN_MB = 1024 * 1024;
    public static final long BYTES_IN_KB = 1024;
    
    // Mensajes de log
    public static final String LOG_UPLOAD_START = "Starting image upload: userId={}, receiverId={}";
    public static final String LOG_UPLOAD_SUCCESS = "Image uploaded successfully: mediaId={}, size={}KB";
    public static final String LOG_UPLOAD_FAILED = "Image upload failed: userId={}, error={}";
    public static final String LOG_DOWNLOAD_START = "Starting image download: mediaId={}, userId={}";
    public static final String LOG_DOWNLOAD_SUCCESS = "Image downloaded successfully: mediaId={}";
    public static final String LOG_DOWNLOAD_FAILED = "Image download failed: mediaId={}, error={}";
    public static final String LOG_DELETE_SUCCESS = "Media deleted successfully: mediaId={}";
    public static final String LOG_CLEANUP_START = "Starting cleanup of delivered media older than {} days";
    public static final String LOG_CLEANUP_COMPLETE = "Cleanup completed: {} media files deleted";
    
    // Mensajes de error
    public static final String ERROR_IMAGE_TOO_LARGE = "Image exceeds maximum size of %d MB";
    public static final String ERROR_INVALID_FORMAT = "Invalid image format. Allowed: %s";
    public static final String ERROR_CORRUPTED_IMAGE = "Image file is corrupted or invalid";
    public static final String ERROR_PROCESSING_FAILED = "Failed to process image";
    public static final String ERROR_STORAGE_FAILED = "Failed to store image";
    public static final String ERROR_NOT_FOUND = "Media not found: %s";
    public static final String ERROR_UNAUTHORIZED = "User not authorized to access this media";
    public static final String ERROR_ALREADY_DELIVERED = "Media already delivered and removed from server";
}