package com.pola.service;

import net.coobird.thumbnailator.Thumbnails;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import com.pola.model.ImageDimensions;
import com.pola.model.ImageMetadata;
import com.pola.model.ImageProcessingResult;

import java.awt.image.BufferedImage;
import java.io.*;

public class ImageProcessingService {
    
    // Configuración
    private static final int MAX_IMAGE_WIDTH = 1920; // Full HD
    private static final int MAX_IMAGE_HEIGHT = 1920;
    private static final float JPEG_QUALITY = 0.85f; // 85%
    private static final float WEBP_QUALITY = 0.85f;
    
    /**
     * Procesa una imagen: comprime y redimensiona
     * @param inputFile Archivo original
     * @return ImageProcessingResult con la imagen comprimida
     */
    public ImageProcessingResult processImage(File inputFile) throws IOException {
        BufferedImage originalImage = ImageIO.read(inputFile);
        
        if (originalImage == null) {
            throw new IOException("No se pudo leer la imagen");
        }
        
        byte[] compressedImageBytes = compressImage(originalImage);
        
        ImageMetadata metadata = new ImageMetadata(
            originalImage.getWidth(),
            originalImage.getHeight(),
            compressedImageBytes.length
        );
        
        return new ImageProcessingResult(compressedImageBytes, metadata);
    }
    
    /**
     * Comprime imagen completa a tamaño máximo con buena calidad
     */
    private byte[] compressImage(BufferedImage originalImage) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        Thumbnails.Builder<BufferedImage> builder = Thumbnails.of(originalImage);
        
        if (originalImage.getWidth() > MAX_IMAGE_WIDTH || 
            originalImage.getHeight() > MAX_IMAGE_HEIGHT) {
            builder.size(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT);
        } else {
            builder.scale(1.0);
        }
        
        builder.outputFormat("jpg")
               .outputQuality(JPEG_QUALITY)
               .toOutputStream(outputStream);
        
        return outputStream.toByteArray();
    }
    
    /**
     * Versión ALTERNATIVA: Comprime a WebP (mejor compresión)
     */
    public byte[] compressImageToWebP(BufferedImage originalImage) throws IOException {
        // Redimensionar si es necesario
        BufferedImage resized = resizeIfNeeded(originalImage);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        // WebP usando ImageIO (requiere webp-imageio)
        ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        
        if (writeParam.canWriteCompressed()) {
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(WEBP_QUALITY);
        }
        
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(resized, null, null), writeParam);
        } finally {
            writer.dispose();
        }
        
        return outputStream.toByteArray();
    }
    
    /**
     * Redimensiona imagen si excede límites
     */
    private BufferedImage resizeIfNeeded(BufferedImage image) throws IOException {
        if (image.getWidth() <= MAX_IMAGE_WIDTH && 
            image.getHeight() <= MAX_IMAGE_HEIGHT) {
            return image;
        }
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Thumbnails.of(image)
            .size(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT)
            .outputFormat("png") // Temporal
            .toOutputStream(baos);
        
        return ImageIO.read(new ByteArrayInputStream(baos.toByteArray()));
    }
    
    /**
     * Convierte bytes a BufferedImage (para mostrar en cliente)
     */
    public BufferedImage bytesToImage(byte[] imageBytes) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
        return ImageIO.read(bais);
    }
    
    /**
     * Valida que el archivo sea una imagen válida
     */
    public boolean isValidImage(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            return image != null;
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * Obtiene dimensiones sin cargar toda la imagen en memoria
     */
    public ImageDimensions getImageDimensions(File file) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            var readers = ImageIO.getImageReaders(iis);
            if (readers.hasNext()) {
                var reader = readers.next();
                try {
                    reader.setInput(iis);
                    return new ImageDimensions(
                        reader.getWidth(0),
                        reader.getHeight(0)
                    );
                } finally {
                    reader.dispose();
                }
            }
        }
        throw new IOException("No se pudo leer dimensiones de la imagen");
    }
}

