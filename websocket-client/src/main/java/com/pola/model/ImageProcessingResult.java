package com.pola.model;

public class ImageProcessingResult {
    private final byte[] thumbnail;
    private final byte[] compressedImage;
    private final ImageMetadata metadata;
    
    public ImageProcessingResult(byte[] thumbnail, byte[] compressedImage, 
                                  ImageMetadata metadata) {
        this.thumbnail = thumbnail;
        this.compressedImage = compressedImage;
        this.metadata = metadata;
    }
    
    // Getters
    public byte[] getThumbnail() { return thumbnail; }
    public byte[] getCompressedImage() { return compressedImage; }
    public ImageMetadata getMetadata() { return metadata; }
}
