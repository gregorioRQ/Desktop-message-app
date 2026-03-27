package com.pola.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ImageChatMessage extends ChatMessage {
    private final StringProperty fullImageUrl;
    private final StringProperty mediaId;
    private final int originalWidth;
    private final int originalHeight;

    public ImageChatMessage(String contactUsername, String senderId, 
                          String fullImageUrl, String mediaId, int width, int height) {
        super(contactUsername, senderId, "[Imagen]", senderId);
        this.fullImageUrl = new SimpleStringProperty(fullImageUrl);
        this.mediaId = new SimpleStringProperty(mediaId);
        this.originalWidth = width;
        this.originalHeight = height;
    }

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