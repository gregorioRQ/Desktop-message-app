package com.pola.service;

import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import com.google.protobuf.Message;
import com.pola.config.HttpConfig;

public class HttpServiceImpl implements HttpService{

    private final HttpClient httpClient;
    private final String baseUrl;

    public HttpServiceImpl(){
        this.baseUrl = HttpConfig.PROFILE_SERVICE_URL;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public <T, R> CompletableFuture<R> createProfile(T request, Class<R> responseClass) {
        return sendPostRequest("/register", request, responseClass);
    }

    @Override
    public <T, R> CompletableFuture<R> login(T request, Class<R> responseClass) {
        return sendPostRequest("/auth/login", request, responseClass);
    }

    @Override
    public <R> CompletableFuture<R> getProfile(String userId, Class<R> responseClass) {
        return sendGetRequest("/profiles/" + userId, responseClass);
    }

    @Override
    public <T, R> CompletableFuture<R> updateProfile(String userId, T request, Class<R> responseClass) {
        return sendPutRequest("/profiles/" + userId, request, responseClass);
    }

    @Override
    public <T, R> CompletableFuture<R> refreshToken(T request, Class<R> responseClass) {
        return sendPostRequest("/auth/refresh", request, responseClass);
    }

    /**
     * Envía una petición POST con Protobuf
     */
    private <T, R> CompletableFuture<R> sendPostRequest(String endpoint, T request, Class<R> responseClass) {
        try {
            byte[] requestBody = serializeProtobuf(request);
            
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Content-Type", "application/x-protobuf")
                    .header("Accept", "application/x-protobuf")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> handleResponse(response, responseClass));
                    
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Envía una petición GET
     */
    private <R> CompletableFuture<R> sendGetRequest(String endpoint, Class<R> responseClass) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Accept", "application/x-protobuf")
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> handleResponse(response, responseClass));
                    
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Envía una petición PUT con Protobuf
     */
    private <T, R> CompletableFuture<R> sendPutRequest(String endpoint, T request, Class<R> responseClass) {
        try {
            byte[] requestBody = serializeProtobuf(request);
            
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Content-Type", "application/x-protobuf")
                    .header("Accept", "application/x-protobuf")
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();
            
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> handleResponse(response, responseClass));
                    
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Envia una peticion DELETE con protobuf
     */
    private <T, R> CompletableFuture<R> sendDeleteRequest(String endpoint, T request, Class<R> responseClass){
        try {
            byte[] requestBody = serializeProtobuf(request);

            HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint))
            .header("Content-Type", "application/x-protobuf")
            .header("Accept", "application/x-protobuf")
            .method("DELETE", HttpRequest.BodyPublishers.ofByteArray(requestBody))
            .timeout(Duration.ofSeconds(30))
            .build();
            
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(response -> handleResponse(response, responseClass));
                    
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Serializa un mensaje Protobuf a bytes
     */
    private <T> byte[] serializeProtobuf(T request) {
        if (request instanceof Message) {
            return ((Message) request).toByteArray();
        }
        throw new IllegalArgumentException("Request debe ser un mensaje Protobuf");
    }
    
    /**
     * Maneja la respuesta HTTP y deserializa el Protobuf
     */
    private <R> R handleResponse(HttpResponse<byte[]> response, Class<R> responseClass) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            try {
                return deserializeProtobuf(response.body(), responseClass);
            } catch (Exception e) {
                throw new RuntimeException("Error al deserializar respuesta", e);
            }
        } else {
            throw new RuntimeException("HTTP Error: " + response.statusCode());
        }
    }
    
    /**
     * Deserializa bytes a un mensaje Protobuf
     */
    private <R> R deserializeProtobuf(byte[] data, Class<R> responseClass) throws Exception {
        Method parseFromMethod = responseClass.getMethod("parseFrom", byte[].class);
        return responseClass.cast(parseFromMethod.invoke(null, (Object) data));
    }
}
