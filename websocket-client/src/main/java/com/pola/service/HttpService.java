package com.pola.service;

import java.util.concurrent.CompletableFuture;

import com.pola.proto.UploadImageRequest;
import com.pola.proto.UploadImageResponse;

/**
 * Interface para el servicio HTTP
 * Principio SOLID: Dependency Inversion - Los clientes dependen de esta abstracción
 * Maneja operaciones REST con perfil-service
 */
public interface HttpService {
    /**
     * Crea un nuevo perfil de usuario
     * @param request Request de creación de perfil (Protobuf)
     * @return CompletableFuture con la respuesta
     */
    <T, R> CompletableFuture<R> createProfile(T request, Class<R> responseClass);
    
    /**
     * Realiza login de usuario
     * @param request Request de login (Protobuf)
     * @return CompletableFuture con la respuesta
     */
    <T, R> CompletableFuture<R> login(T request, Class<R> responseClass);
    
    /**
     * Obtiene información del perfil
     * @param userId ID del usuario
     * @return CompletableFuture con la respuesta
     */
    <R> CompletableFuture<R> getProfile(String userId, Class<R> responseClass);
    
    /**
     * Actualiza información del perfil
     * @param userId ID del usuario
     * @param request Request de actualización (Protobuf)
     * @return CompletableFuture con la respuesta
     */
    <T, R> CompletableFuture<R> updateProfile(String userId, T request, Class<R> responseClass);

    /**
     * Refresca los tokens de sesión
     * @param request Request de refresco (Protobuf)
     * @return CompletableFuture con la respuesta
     */
    <T, R> CompletableFuture<R> refreshToken(T request, Class<R> responseClass);

    /**
     * Cierra la sesión del usuario
     * @param request Request de logout (Protobuf)
     * @return CompletableFuture con la respuesta
     */
    <T, R> CompletableFuture<R> logout(T request, String accessToken, Class<R> responseClass);

    /**
     * Obtiene el historial de mensajes 
    */

    /**
     * Obtiene el contacto/lista de contactos del usuario
     */

    /**
     * Envía un latido al servidor para mantener la sesión activa.
     * @param accessToken Token de acceso.
     * @return CompletableFuture indicando si el latido fue exitoso.
     */
    CompletableFuture<Boolean> sendHeartbeat(String accessToken);

    /**
     * Sube un archivo multimedia (imagen) al servidor usando Protobuf.
     * @param request Petición de subida con datos de imagen.
     * @param accessToken Token de autorización.
     * @return CompletableFuture con la respuesta del servidor (UploadImageResponse).
     */
    CompletableFuture<UploadImageResponse> uploadMedia(UploadImageRequest request, String accessToken);
}
