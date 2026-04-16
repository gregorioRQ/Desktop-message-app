package com.pola.service;

import java.io.File;
import java.io.IOException;

import com.pola.controller.ChatController;
import com.pola.proto.UploadImageRequest;
import com.pola.model.ImageProcessingResult;
import com.pola.service.HttpService;
import com.pola.service.ImageProcessingService;
import com.pola.view.ChatDialogs;

import javafx.application.Platform;
import javafx.stage.FileChooser;

/**
 * Helper para gestionar la selección, procesamiento y subida de imágenes.
 * Sigue el patrón de separar la lógica de acciones del controlador principal.
 */
public class ImageActionHelper {
    
    private final ChatController chatController;
    private final HttpService httpService;
    private final ImageProcessingService imageProcessor;

    public ImageActionHelper(ChatController chatController, HttpService httpService) {
        this.chatController = chatController;
        this.httpService = httpService;
        this.imageProcessor = new ImageProcessingService();
    }

    public void handleAttachImage() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Seleccionar Imagen");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Imágenes", "*.png", "*.jpg", "*.jpeg", "*.gif")
        );

        // Usar la ventana actual para el diálogo modal
        File file = fileChooser.showOpenDialog(chatController.getMessageInput().getScene().getWindow());
        
        if (file != null) {
            processAndUpload(file);
        }
    }

    private void processAndUpload(File imageFile) {
        // 1. Validar tamaño original
        if (imageFile.length() > 28 * 1024 * 1024) {
            ChatDialogs.showError("Error", "Imagen muy grande (máx 28 MB)");
            return;
        }

        // Ejecutar procesamiento en hilo separado para no congelar la UI
        new Thread(() -> {
            try {
                // 2. Procesar localmente
                ImageProcessingResult result = imageProcessor.processImage(imageFile);
                System.out.println("Compresión exitosa: " + result.getMetadata());

                // 3. Subir al servidor
                uploadToServer(result);

            } catch (IOException e) {
                Platform.runLater(() -> 
                    ChatDialogs.showError("Error", "Error procesando imagen: " + e.getMessage())
                );
            }
        }).start();
    }

    private void uploadToServer(ImageProcessingResult result) {
        String token = chatController.getAuthToken();
        String currentUserId = chatController.getCurrentUserId();
        String receiverId = chatController.getSelectedContact() != null ? chatController.getSelectedContact().getContactUsername() : "";

        UploadImageRequest request = UploadImageRequest.newBuilder()
            .setUserId(currentUserId)
            .setReceiverId(receiverId)
            .setImageData(com.google.protobuf.ByteString.copyFrom(result.getCompressedImage()))
            .setOriginalWidth(result.getMetadata().getOriginalWidth())
            .setOriginalHeight(result.getMetadata().getOriginalHeight())
            // .setOriginalFilename(file.getName()) // Si quisieras pasar el nombre
            .build();

        httpService.uploadMedia(request, token)
            .thenAccept(response -> {
                System.out.println("Upload exitoso. MediaID: " + response.getMediaId());
                Platform.runLater(() -> 
                    chatController.getMessageActionHelper().handleSendImage(result, response)
                );
            })
            .exceptionally(e -> {
                Platform.runLater(() -> 
                    ChatDialogs.showError("Error de subida", e.getMessage())
                );
                return null;
            });
    }
}