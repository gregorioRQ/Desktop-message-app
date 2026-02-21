package com.pola.model;

public class ImageDimensions {
    private final int width;
    private final int height;
    
    public ImageDimensions(int width, int height) {
        this.width = width;
        this.height = height;
    }
    
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
