package com.pola.model;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ImageChatMessage extends ChatMessage {
    private final ObjectProperty<byte[]> thumbnailData;
    private final StringProperty fullImageUrl;
    private final StringProperty mediaId;
    private final int originalWidth;
    private final int originalHeight;

    public ImageChatMessage(String contactUsername, String senderId, byte[] thumbnailData, 
                          String fullImageUrl, String mediaId, int width, int height) {
        super(contactUsername, "[Imagen]", senderId); // Texto fallback para notificaciones simples
        this.thumbnailData = new SimpleObjectProperty<>(thumbnailData);
        this.fullImageUrl = new SimpleStringProperty(fullImageUrl);
        this.mediaId = new SimpleStringProperty(mediaId);
        this.originalWidth = width;
        this.originalHeight = height;
    }

    public byte[] getThumbnailData() { return thumbnailData.get(); }
    public ObjectProperty<byte[]> thumbnailDataProperty() { return thumbnailData; }
    
    public String getFullImageUrl() { return fullImageUrl.get(); }
    public StringProperty fullImageUrlProperty() { return fullImageUrl; }
    
    public String getMediaId() { return mediaId.get(); }
    public StringProperty mediaIdProperty() { return mediaId; }

    public int getOriginalWidth() { return originalWidth; }
    public int getOriginalHeight() { return originalHeight; }

    @Override
    public String getDisplayText(String currentUserId) {
        return String.format("[%s] %s: [Foto]", getFormattedTime(), getSenderId().equals(currentUserId) ? "Tú" : "Contacto");
    }
}