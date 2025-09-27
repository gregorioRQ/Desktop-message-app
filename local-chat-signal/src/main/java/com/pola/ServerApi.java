package com.pola;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ServerApi {
    private static final String SERVER_URL = "http://localhost:8083/auth";
    private static final String SERVER_CHAT_URL = "http://localhost:8085/message";
    private static final OkHttpClient client1 = new OkHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final MediaType JSON_MEDIA = MediaType.parse("application/json; charset=utf-8");

    public static void registerUser(RequestRegister dto) throws Exception {

        String json = mapper.writeValueAsString(dto);

        RequestBody body = RequestBody.create(json, JSON_MEDIA);

        Request request = new Request.Builder()
                .url(SERVER_URL + "/register")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build();

        try (Response response = client1.newCall(request).execute()) {
            if (response.isSuccessful()) {
                System.out.println("Usuario registrado en servidor con éxito");
            } else {
                throw new RuntimeException("Error en registro: HTTP " + response.code());
            }
        }
    }

    public static PreKeyBundleDTO getPreKeyBundle(String username) {
        String url = SERVER_URL + "/prekeybundle/" + URLEncoder.encode(username, StandardCharsets.UTF_8);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = client1.newCall(request).execute()) {
            if (response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : null;
                if (body == null || body.isEmpty()) {
                    return null;
                }
                return mapper.readValue(body, PreKeyBundleDTO.class);
            } else {
                throw new RuntimeException("Error obteniendo PreKeyBundle: HTTP " + response.code());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    public static void sendMessage(MessageRequest mr) {
        try {
            String json = mapper.writeValueAsString(mr);

            RequestBody body = RequestBody.create(json, JSON_MEDIA);

            Request request = new Request.Builder()
                    .url(SERVER_CHAT_URL)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client1.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    System.out.println("Mensaje enviado con éxito");
                } else {
                    throw new RuntimeException("Error en envío: HTTP " + response.code());
                }
            }
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            ex.printStackTrace();
        }

    }

    public static List<MessageResponse> getMessages(String username) {
        String url = SERVER_CHAT_URL + "/messages-unread/" + URLEncoder.encode(username, StandardCharsets.UTF_8);

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = client1.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Error en la petición: HTTP " + response.code());
            }

            String body = response.body() != null ? response.body().string() : "";
            return mapper.readValue(body, new TypeReference<List<MessageResponse>>() {
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Hace una peticion rest al servidor para
     * Eliminr de la db del servidor todos los mensajes entre el remitente
     * y el receptor especificados
     * 
     */
    public static void deleteMessageBetweenUsers(String sender, String receiver) {
        String url = SERVER_CHAT_URL + "/delete-between?sender=" + URLEncoder.encode(sender, StandardCharsets.UTF_8)
                + "&receiver=" + URLEncoder.encode(receiver, StandardCharsets.UTF_8);

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .addHeader("Accept", "application/json")
                .build();

        try (Response response = client1.newCall(request).execute()) {
            if (response.isSuccessful()) {
                System.out.println("Mensajes entre " + sender + " y " + receiver + " eliminados con éxito");
            } else {
                throw new RuntimeException("Error eliminando mensajes: HTTP " + response.code());
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void readMessages(String receiver, List<Long> messageIds) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("receiver", receiver);
            payload.put("messageIds", messageIds);
            String json = mapper.writeValueAsString(payload);

            RequestBody body = RequestBody.create(json, JSON_MEDIA);
            Request request = new Request.Builder()
                    .url(SERVER_CHAT_URL + "/read")
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            try (Response response = client1.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    System.out.println("Marcado batch OK");
                } else {
                    throw new RuntimeException("Error batch read: HTTP " + response.code());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

}
