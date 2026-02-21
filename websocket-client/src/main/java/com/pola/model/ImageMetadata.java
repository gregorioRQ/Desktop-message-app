package com.pola.model;

import java.util.concurrent.CompletableFuture;

public class ImageMetadata {
    private final int originalWidth;
    private final int originalHeight;
    private final long thumbnailSize;
    private final long compressedSize;
    
    public ImageMetadata(int width, int height, long thumbSize, long compSize) {
        this.originalWidth = width;
        this.originalHeight = height;
        this.thumbnailSize = thumbSize;
        this.compressedSize = compSize;
    }
    
    // Getters
    public int getOriginalWidth() { return originalWidth; }
    public int getOriginalHeight() { return originalHeight; }
    public long getThumbnailSize() { return thumbnailSize; }
    public long getCompressedSize() { return compressedSize; }
    
    @Override
    public String toString() {
        return String.format("Original: %dx%d, Thumbnail: %d KB, Compressed: %d KB",
            originalWidth, originalHeight, 
            thumbnailSize / 1024, 
            compressedSize / 1024);
    }

}
