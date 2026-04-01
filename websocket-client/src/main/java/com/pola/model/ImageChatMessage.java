package com.pola.model;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class ImageChatMessage extends ChatMessage {
    private final StringProperty fullImageUrl;
    private final StringProperty mediaId;
    private final int originalWidth;
    private final int originalHeight;
    private final SimpleBooleanProperty downloaded;
    private final StringProperty thumbnailPath;

    public ImageChatMessage(String contactUsername, String senderId, 
                          String fullImageUrl, String mediaId, int width, int height) {
        super(contactUsername, senderId, "[Imagen]", senderId);
        this.fullImageUrl = new SimpleStringProperty(fullImageUrl);
        this.mediaId = new SimpleStringProperty(mediaId);
        this.originalWidth = width;
        this.originalHeight = height;
        this.downloaded = new SimpleBooleanProperty(false);
        this.thumbnailPath = new SimpleStringProperty(null);
    }

    public String getFullImageUrl() { return fullImageUrl.get(); }
    public StringProperty fullImageUrlProperty() { return fullImageUrl; }
    public void setFullImageUrl(String url) { this.fullImageUrl.set(url); }
    
    public String getMediaId() { return mediaId.get(); }
    public StringProperty mediaIdProperty() { return mediaId; }

    public int getOriginalWidth() { return originalWidth; }
    public int getOriginalHeight() { return originalHeight; }
    
    public boolean isDownloaded() { return downloaded.get(); }
    public SimpleBooleanProperty downloadedProperty() { return downloaded; }
    public void setDownloaded(boolean value) { downloaded.set(value); }
    
    public String getThumbnailPath() { return thumbnailPath.get(); }
    public StringProperty thumbnailPathProperty() { return thumbnailPath; }
    public void setThumbnailPath(String path) { this.thumbnailPath.set(path); }

    @Override
    public String getDisplayText(String currentUserId) {
        return String.format("[%s] %s: [Foto]", getFormattedTime(), getSenderId().equals(currentUserId) ? "Tú" : "Contacto");
    }
}