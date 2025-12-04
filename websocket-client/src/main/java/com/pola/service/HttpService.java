package com.pola.service;

import java.util.concurrent.CompletableFuture;

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
}
