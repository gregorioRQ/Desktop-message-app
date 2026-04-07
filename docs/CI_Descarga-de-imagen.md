# Contrato de Interfaz: Descarga de Imagen

## Descripción General

Este documento describe el flujo de descarga de imágenes del servidor. El cliente solicita una imagen al media-service mediante su mediaId y recibe los datos binarios de la imagen.

---

## Request

### Solicitud de Descarga de Imagen (`DownloadImageRequest`)
- **Campo**: `media_id` (string)  
  **Descripción**: Identificador único de la imagen a descargar.  
  **Obligatorio**: Sí.
- **Campo**: `user_id` (string)  
  **Descripción**: Identificador del usuario que solicita la descarga. Se utiliza para verificar permisos.  
  **Obligatorio**: Sí.

---

## Response

### Respuesta de Descarga de Imagen (`DownloadImageResponse`)
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la operación de descarga fue exitosa.  
  **Valores posibles**: `true` o `false`.
- **Campo**: `image_data` (bytes)  
  **Descripción**: Datos binarios de la imagen descargada.
- **Campo**: `mime_type` (string)  
  **Descripción**: Tipo MIME de la imagen (ej: `image/jpeg`, `image/png`).
- **Campo**: `width` (int32)  
  **Descripción**: Ancho de la imagen en píxeles.
- **Campo**: `height` (int32)  
  **Descripción**: Alto de la imagen en píxeles.
- **Campo**: `error_message` (string)  
  **Descripción**: Mensaje de error si la operación falló.

---

## Medio de Comunicación

- **Protocolo**: HTTP
- **Formato de Mensajes**: Protobuf (`application/x-protobuf`)

---

## Endpoint

- **Método**: POST
- **URL**: `/api/v1/media/download`
- **Content-Type**: `application/x-protobuf`

### Endpoint Alternativo

- **Método**: GET
- **URL**: `/api/v1/media/download/{mediaId}`
- **Header**: `X-User-Id` (identificador del usuario)
- **Response**: `application/octet-stream` (bytes de la imagen)

---

## Flujo

El usuario hace clic en una imagen recibida o en la descarga desde el chat. El cliente envía una solicitud HTTP con el mediaId al media-service. El servidor verifica que el usuario tenga permisos para descargar la imagen. Si tiene permisos, retrieves los datos binarios y los retorna al cliente. El cliente muestra la imagen en la interfaz.

---

## Estados de Respuesta

| success | image_data | mime_type | error_message | Descripción |
|---------|-----------|----------|-------------|-------------|
| true | (datos) | (tipo) | "" | Imagen descargada exitosamente |
| false | "" | "" | "Image not found" | Imagen no encontrada |
| false | "" | "" | "Access denied" | Usuario no tiene permisos |