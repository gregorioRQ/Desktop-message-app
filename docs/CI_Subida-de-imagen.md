# Contrato de Interfaz: Subida de Imagen

## Descripción General

Este documento describe el flujo de subida de imágenes al servidor. El cliente envía la imagen completa al media-service, que la procesa, almacena y retorna una URL pública para acceder a la imagen posteriormente.

---

## Request

### Solicitud de Subida de Imagen (`UploadImageRequest`)
- **Campo**: `user_id` (string)  
  **Descripción**: Identificador del usuario que envía la imagen.  
  **Obligatorio**: Sí.
- **Campo**: `receiver_id` (string)  
  **Descripción**: Identificador del usuario que recibirá la imagen.  
  **Obligatorio**: Sí.
- **Campo**: `image_data` (bytes)  
  **Descripción**: Datos binarios de la imagen.  
  **Obligatorio**: Sí.
- **Campo**: `original_filename` (string)  
  **Descripción**: Nombre original del archivo de imagen.  
  **Obligatorio**: No.
- **Campo**: `original_width` (int32)  
  **Descripción**: Ancho original de la imagen en píxeles.  
  **Obligatorio**: No.
- **Campo**: `original_height` (int32)  
  **Descripción**: Alto original de la imagen en píxeles.  
  **Obligatorio**: No.

---

## Response

### Respuesta de Subida de Imagen (`UploadImageResponse`)
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la operación de subida fue exitosa.  
  **Valores posibles**: `true` o `false`.
- **Campo**: `media_id` (string)  
  **Descripción**: Identificador único asignado a la imagen subida.
- **Campo**: `full_image_url` (string)  
  **Descripción**: URL pública para acceder a la imagen.
- **Campo**: `full_image_size` (int64)  
  **Descripción**: Tamaño del archivo de imagen en bytes.
- **Campo**: `error_message` (string)  
  **Descripción**: Mensaje de error si la operación falló.

---

## Medio de Comunicación

- **Protocolo**: HTTP
- **Formato de Mensajes**: Protobuf (`application/x-protobuf`)

---

## Endpoint

- **Método**: POST
- **URL**: `/api/v1/media/upload`
- **Content-Type**: `application/x-protobuf`

---

## Flujo

El usuario selecciona una imagen para enviar desde la aplicación de escritorio. El clientecodifica la imagen en bytes y la envía al media-service junto con el userId del remisor y del receptor. El servidor procesa la imagen, la almacena en el sistema de archivos, genera un mediaId único y retorna la URL pública. El cliente recibe una notificación por WebSocket (ImageMessage) con la información de la imagen para mostrar al receptor.

---

## Estados de Respuesta

| success | media_id | full_image_url | error_message | Descripción |
|---------|----------|---------------|---------------|-------------|
| true | (generado) | (URL) | "" | Imagen subida exitosamente |
| false | "" | "" | "Server error: ..." | Error al procesar la imagen |