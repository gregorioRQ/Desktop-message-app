package com.pola.media_service.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Service;

import com.pola.media_service.config.MediaServiceProperties;
import com.pola.media_service.dto.ImageProcessingResult;
import com.pola.media_service.exception.ImageProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

import static com.pola.media_service.constants.MediaConstants.*;

/**
 * Servicio de procesamiento de imágenes.
 * Responsable de comprimir, redimensionar y convertir imágenes a WebP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageProcessingService {

    private final MediaServiceProperties properties;
    
    /**
     * Procesa una imagen: la recomprime, convierte a WebP y genera thumbnail.
     * 
     * @param imageData Datos de la imagen original (ya comprimida desde cliente)
     * @param originalFilename Nombre del archivo original
     * @return ImageProcessingResult con thumbnail y imagen completa procesada
     * @throws ImageProcessingException si falla el procesamiento
     */
    public ImageProcessingResult processImage(byte[] imageData, String originalFilename) {
        log.info("Starting image processing: filename={}, size={}KB", 
            originalFilename, imageData.length / BYTES_IN_KB);
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Leer imagen original
            BufferedImage originalImage = readImage(imageData);
            
            // Obtener dimensiones originales
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            String originalFormat = detectFormat(originalFilename);
            
            log.debug("Original image dimensions: {}x{}, format={}", 
                originalWidth, originalHeight, originalFormat);
            
            // Generar thumbnail
            byte[] thumbnailData = generateThumbnail(originalImage);
            log.debug("Thumbnail generated: size={}KB", thumbnailData.length / BYTES_IN_KB);
            
            // Procesar imagen completa (redimensionar si es necesario y convertir a WebP)
            ImageProcessingResult.ImageProcessingResultBuilder resultBuilder = 
                ImageProcessingResult.builder();
            
            byte[] fullImageData;
            boolean wasResized = false;
            
            if (needsResize(originalWidth, originalHeight)) {
                log.debug("Image needs resizing from {}x{} to max {}x{}", 
                    originalWidth, originalHeight,
                    properties.getProcessing().getMaxImageWidth(),
                    properties.getProcessing().getMaxImageHeight());
                
                BufferedImage resizedImage = resizeImage(originalImage);
                fullImageData = convertToWebP(resizedImage, properties.getProcessing().getWebpQuality());
                wasResized = true;
            } else {
                log.debug("Image within size limits, converting to WebP");
                fullImageData = convertToWebP(originalImage, properties.getProcessing().getWebpQuality());
            }
            
            // Calcular ratio de compresión
            float compressionRatio = (float) fullImageData.length / imageData.length;
            
            long processingTime = System.currentTimeMillis() - startTime;
            log.info("Image processing completed: originalSize={}KB, finalSize={}KB, " +
                    "compressionRatio={}, thumbnailSize={}KB, time={}ms",
                imageData.length / BYTES_IN_KB,
                fullImageData.length / BYTES_IN_KB,
                String.format("%.2f", compressionRatio),
                thumbnailData.length / BYTES_IN_KB,
                processingTime);
            
            return resultBuilder
                .thumbnailData(thumbnailData)
                .fullImageData(fullImageData)
                .originalWidth(originalWidth)
                .originalHeight(originalHeight)
                .thumbnailSize(thumbnailData.length)
                .fullImageSize(fullImageData.length)
                .compressionRatio(compressionRatio)
                .wasResized(wasResized)
                .originalFormat(originalFormat)
                .build();
            
        } catch (IOException e) {
            log.error("Failed to process image: filename={}", originalFilename, e);
            throw new ImageProcessingException(ERROR_PROCESSING_FAILED, e);
        }
    }

    /**
     * Lee una imagen desde un array de bytes.
     * 
     * @param imageData Datos de la imagen
     * @return BufferedImage
     * @throws IOException si falla la lectura
     */
    private BufferedImage readImage(byte[] imageData) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
        
        if (image == null) {
            throw new IOException("Failed to read image data");
        }
        
        return image;
    }

    /**
     * Detecta el formato de una imagen por su nombre de archivo.
     * 
     * @param filename Nombre del archivo
     * @return Formato detectado (jpg, png, webp, etc.)
     */
    private String detectFormat(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "unknown";
        }
        
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return extension;
    }

    /**
     * Determina si una imagen necesita ser redimensionada según los límites configurados.
     * 
     * @param width Ancho actual
     * @param height Alto actual
     * @return true si excede los límites
     */
    private boolean needsResize(int width, int height) {
        return width > properties.getProcessing().getMaxImageWidth() || 
               height > properties.getProcessing().getMaxImageHeight();
    }

    /**
     * Genera un thumbnail pequeño de la imagen.
     * 
     * @param originalImage Imagen original
     * @return Datos del thumbnail en formato WebP
     * @throws IOException si falla la generación
     */
    private byte[] generateThumbnail(BufferedImage originalImage) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        int thumbnailSize = properties.getProcessing().getThumbnailSize();
        
        // Usar Thumbnailator para generar thumbnail manteniendo aspecto
        Thumbnails.of(originalImage)
            .size(thumbnailSize, thumbnailSize)
            .outputFormat(FORMAT_JPEG) // Usar JPEG para thumbnail (más pequeño)
            .outputQuality(properties.getProcessing().getThumbnailQuality())
            .toOutputStream(outputStream);
        
        return outputStream.toByteArray();
    }

    /**
     * Redimensiona una imagen respetando el aspect ratio.
     * 
     * @param originalImage Imagen original
     * @return Imagen redimensionada
     * @throws IOException si falla el redimensionamiento
     */
    private BufferedImage resizeImage(BufferedImage originalImage) throws IOException {
        int maxWidth = properties.getProcessing().getMaxImageWidth();
        int maxHeight = properties.getProcessing().getMaxImageHeight();
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        // Thumbnailator mantiene aspect ratio automáticamente
        Thumbnails.of(originalImage)
            .size(maxWidth, maxHeight)
            .outputFormat(FORMAT_PNG) // PNG temporal para no perder calidad antes de WebP
            .toOutputStream(outputStream);
        
        return ImageIO.read(new ByteArrayInputStream(outputStream.toByteArray()));
    }

     /**
     * Convierte una imagen a formato WebP con la calidad especificada.
     * 
     * @param image Imagen a convertir
     * @param quality Calidad de compresión (0.0 - 1.0)
     * @return Datos de la imagen en formato WebP
     * @throws IOException si falla la conversión
     */
    private byte[] convertToWebP(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        // Buscar ImageWriter para WebP
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByMIMEType(MIME_TYPE_WEBP);
        
        if (!writers.hasNext()) {
            log.warn("WebP writer not available, falling back to JPEG");
            return convertToJPEG(image, quality);
        }
        
        ImageWriter writer = writers.next();
        ImageWriteParam writeParam = writer.getDefaultWriteParam();
        
        try {
            // IMPORTANTE: Establecer modo de compresión ANTES de la calidad
            // Esto es requerido por la librería webp-imageio
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            
            // Establecer el tipo de compresión (lossy para WebP)
            String[] compressionTypes = writeParam.getCompressionTypes();
            if (compressionTypes != null && compressionTypes.length > 0) {
                // Usar el primer tipo disponible (generalmente "Lossy" o "Lossless")
                writeParam.setCompressionType(compressionTypes[0]);
                log.debug("Using WebP compression type: {}", compressionTypes[0]);
            }
            
            // Ahora sí podemos establecer la calidad
            writeParam.setCompressionQuality(quality);
            
            // Escribir la imagen
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), writeParam);
            }
            
            log.debug("Image converted to WebP: quality={}, size={}KB", 
                quality, outputStream.size() / BYTES_IN_KB);
            
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            log.error("Failed to convert to WebP, falling back to JPEG", e);
            return convertToJPEG(image, quality);
            
        } finally {
            writer.dispose();
        }
    }

    /**
     * Fallback: convierte imagen a JPEG si WebP no está disponible.
     * 
     * @param image Imagen a convertir
     * @param quality Calidad de compresión
     * @return Datos de la imagen en formato JPEG
     * @throws IOException si falla la conversión
     */
    private byte[] convertToJPEG(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        Thumbnails.of(image)
            .scale(1.0)
            .outputFormat(FORMAT_JPEG)
            .outputQuality(quality)
            .toOutputStream(outputStream);
        
        return outputStream.toByteArray();
    }
    
    /**
     * Convierte bytes a BufferedImage (útil para mostrar o procesar).
     * 
     * @param imageBytes Datos de la imagen
     * @return BufferedImage
     * @throws IOException si falla la conversión
     */
    public BufferedImage bytesToImage(byte[] imageBytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(imageBytes));
    }

}
