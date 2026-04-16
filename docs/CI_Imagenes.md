# Contrato de Interfaz: Imágenes (WebSocket)

## Descripción General

Este documento describe el flujo de mensajería de imágenes a través de WebSocket. A diferencia de la subida/descarga HTTP que transfiere la imagen completa, los mensajes WebSocket Notifican al receptor sobre la existencia de una nueva imagen, proporcionando metadatos y una URL para visualizarla.

---

## Mensajes Enviados por el Cliente

### Notificación de Imagen (`ImageMessage`)
- **Campo**: `media_id` (string)  
  **Descripción**: Identificador único de la imagen.  
  **Obligatorio**: Sí.
- **Campo**: `sender_id` (string)  
  **Descripción**: Identificador del usuario que envía la imagen.  
  **Obligatorio**: Sí.
- **Campo**: `receiver_id` (string)  
  **Descripción**: Identificador del usuario que recibirá la imagen.  
  **Obligatorio**: Sí.
- **Campo**: `full_image_url` (string)  
  **Descripción**: URL pública para acceder a la imagen.  
  **Obligatorio**: Sí.
- **Campo**: `original_width` (int32)  
  **Descripción**: Ancho original de la imagen en píxeles.  
  **Obligatorio**: No.
- **Campo**: `original_height` (int32)  
  **Descripción**: Alto original de la imagen en píxeles.  
  **Obligatorio**: No.
- **Campo**: `file_size` (int64)  
  **Descripción**: Tamaño del archivo en bytes.  
  **Obligatorio**: No.
- **Campo**: `timestamp` (int64)  
  **Descripción**: Timestamp de cuando se envió la imagen.  
  **Obligatorio**: Sí.
- **Campo**: `status` (MessageStatus)  
  **Descripción**: Estado del mensaje de imagen.  
  **Obligatorio**: Sí.

#### Enum: MessageStatus
| Valor | Descripción |
|-------|----------|
| SENT | Mensaje enviado |
| DELIVERED | Mensaje entregado al receptor |
| READ | Mensaje leído por el receptor |
| FAILED | Fallo en el envío |

### Acknowledgement de Imagen (`ImageAck`)
- **Campo**: `media_id` (string)  
  **Descripción**: Identificador de la imagen que se está confirmando.  
  **Obligatorio**: Sí.
- **Campo**: `received` (bool)  
  **Descripción**: Indica si la imagen fue recibida correctamente.  
  **Obligatorio**: Sí.
- **Campo**: `timestamp` (int64)  
  **Descripción**: Timestamp de la confirmación.  
  **Obligatorio**: Sí.

### Solicitud de Eliminación de Media (`DeleteMediaRequest`)
- **Campo**: `media_id` (string)  
  **Descripción**: Identificador de la imagen a eliminar.  
  **Obligatorio**: Sí.
- **Campo**: `user_id` (string)  
  **Descripción**: Identificador del usuario que solicita la eliminación.  
  **Obligatorio**: Sí.

---

## Mensajes Recibidos por el Cliente

### Acknowledgement de Imagen (`ImageAck`)
- **Campo**: `media_id` (string)  
  **Descripción**: Identificador de la imagen confirmada.
- **Campo**: `received` (bool)  
  **Descripción**: Indica si la imagen fue recibida correctamente.
- **Campo**: `timestamp` (int64)  
  **Descripción**: Timestamp de la confirmación.

### Respuesta de Eliminación de Media (`DeleteMediaResponse`)
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la eliminación fue exitosa.  
  **Valores posibles**: `true` o `false`.
- **Campo**: `media_id` (string)  
  **Descripción**: Identificador de la imagen eliminada.
- **Campo**: `message` (string)  
  **Descripción**: Mensaje descriptivo del resultado.

---

## Medio de Comunicación

- **Protocolo**: WebSocket
- **Formato de Mensajes**: Protobuf (`WsMessage`)

---

## Estructura del Mensaje WebSocket

Todos los mensajes se encapsulan en un `WsMessage`:

```json
{
  "imageMessage": { ... }
}
```

```json
{
  "imageAck": { ... }
}
```

```json
{
  "deleteMediaRequest": { ... }
}
```

```json
{
  "deleteMediaResponse": { ... }
}
```

---

## Flujo

El usuario envía una imagen al media-service mediante HTTP. El servidor almacena la imagen y retorna una URL pública. El cliente envía un ImageMessage por WebSocket al receptor con la URL y metadatos de la imagen. El receptor recibe la notificación y puede visualizar la imagen accediendo a la URL. Cuando el receptor descarga la imagen, envía un ImageAck al remitente Confirmando la recepción. El cliente puede solicitar la eliminación de una imagen enviando un DeleteMediaRequest.

---

## Lista de Mensajes Pendientes de Imagen

### Mensajes Recibidos por el Cliente

#### Lista de Imágenes Pendientes (`UnreadImageMessagesList`)
- **Campo**: `messages` (ImageMessage[])  
  **Descripción**: Lista de imágenes enviadas mientras el usuario estaba offline.

---

## Notas

- La transferencia de la imagen completa se realiza por HTTP (`/api/v1/media/upload` y `/api/v1/media/download`).
- WebSocket solo se utiliza para Notificar sobre la existencia de la imagen y su estado.
- Para más detalles de los endpoints HTTP, consultar `CI_Subida-de-imagen.md` y `CI_Descarga-de-imagen.md`.