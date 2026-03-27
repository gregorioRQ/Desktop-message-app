package com.pola.model;

public class ImageMetadata {
    private final int originalWidth;
    private final int originalHeight;
    private final long compressedSize;
    
    public ImageMetadata(int width, int height, long compSize) {
        this.originalWidth = width;
        this.originalHeight = height;
        this.compressedSize = compSize;
    }
    
    public int getOriginalWidth() { return originalWidth; }
    public int getOriginalHeight() { return originalHeight; }
    public long getCompressedSize() { return compressedSize; }
    
    @Override
    public String toString() {
        return String.format("Original: %dx%d, Compressed: %d KB",
            originalWidth, originalHeight, 
            compressedSize / 1024);
    }

}
