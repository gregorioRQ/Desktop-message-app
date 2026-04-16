package com.pola.model;

public class ImageProcessingResult {
    private final byte[] compressedImage;
    private final ImageMetadata metadata;
    
    public ImageProcessingResult(byte[] compressedImage, ImageMetadata metadata) {
        this.compressedImage = compressedImage;
        this.metadata = metadata;
    }
    
    public byte[] getCompressedImage() { return compressedImage; }
    public ImageMetadata getMetadata() { return metadata; }
}
