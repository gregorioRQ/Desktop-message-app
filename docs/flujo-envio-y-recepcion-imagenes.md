# Flujo de Envío y Recepción de Imágenes

Este documento describe el flujo completo de envío y recepción de imágenes, desde que un usuario selecciona una imagen hasta que el receptor la visualiza. El sistema utiliza HTTP para la subida/descarga de archivos y WebSocket para las notificaciones, con soporte para receptores online y offline.

---

## 1. Arquitectura General

### Servicios Involucrados

| Servicio | Puerto | Responsabilidad |
|----------|--------|-----------------|
| media-service | 8086 | Almacenamiento y entrega de archivos de imagen |
| connection-service | 8083 | Enruta mensajes de imagen por WebSocket |
| chat-service | 8085 | Gestiona mensajes de imagen pendientes |
| notification-service | 8084 | Envía notificaciones push mediante SSE |

### Colas de RabbitMQ

El sistema utiliza el exchange `message.exchange` con las siguientes colas:

| Cola | Routing Key | Propósito | Productor | Consumidor |
|------|-------------|-----------|-----------|------------|
| `message.sent.{instanceId}` | `{instanceId}` | Mensajes para usuarios online en instancia específica | connection-service | chat-service |
| `message.offline` | `offline` | Mensajes para usuarios offline | connection-service | chat-service |
| `message.notification` | `notification` | Notificaciones push de nuevos mensajes de imagen | connection-service | notification-service |
| `message.delivery` | `delivery` | Estados de entrega (DELIVERED, READ) | chat-service | connection-service |

### Canales de Comunicación

- **HTTP**: Subida y descarga de imágenes al media-service
- **WebSocket**: Notificaciones de mensajes de imagen al receptor
- **SSE (Server-Sent Events)**: Notificaciones push al cliente
- **REST API**: Recuperación de mensajes pendientes

### Flujo General

```
[Cliente A]                    [media-service]              [Cliente B]
     │                              │                            │
     │── HTTP POST /upload ────────>│                            │
     │   (imagen en bytes)          │── Genera mediaId         │
     │                              │── Guarda archivo          │
     │<── HTTP 200 (mediaId, URL) ──│                            │
     │                              │                            │
     │── WebSocket ImageMessage ────>│                            │
     │   (con mediaId y URL)        │                            │
     │                              │── WebSocket ───────────────>│
     │                              │   (ImageMessage)            │
     │                              │                            │── Usuario hace clic
     │                              │                            │── HTTP GET /download
     │                              │<────────────────────────────│
     │                              │── HTTP 200 (bytes imagen)  │
     │                              │                            │── Muestra imagen
```

---

## 2. Flujo Detallado Paso a Paso

### Paso 1: Usuario selecciona imagen

El usuario selecciona una imagen de su dispositivo para enviar.

**Qué ocurre:**
- El cliente JavaFX abre un selector de archivos
- El usuario selecciona una imagen
- Se lee el archivo y se codifica en bytes
- Se obtienen metadatos opcionales (ancho, alto, nombre original)

---

### Paso 2: Cliente sube imagen al media-service

El cliente envía la imagen por HTTP al media-service.

**Endpoint:**
```
POST /api/v1/media/upload
Content-Type: application/x-protobuf
```

**Datos del request:**
```
UploadImageRequest:
  - userId: ID del usuario que envía
  - receiverId: ID del usuario que recibirá
  - imageData: bytes de la imagen
  - originalFilename: nombre original del archivo
  - originalWidth: ancho en píxeles
  - originalHeight: alto en píxeles
```

**Qué ocurre en media-service:**
1. Se genera un `mediaId` único (UUID)
2. Se procesa la imagen
3. Se guarda en el sistema de archivos
4. Se genera una URL pública para acceso

**Respuesta:**
```
UploadImageResponse:
  - success: true/false
  - mediaId: identificador único generado
  - fullImageUrl: URL pública para acceder a la imagen
  - fullImageSize: tamaño en bytes
  - errorMessage: mensaje de error si falló
```

---

### Paso 3: Cliente envía ImageMessage por WebSocket

Una vez que la imagen está subida, el cliente notifica al receptor.

**Datos del mensaje:**
```
ImageMessage:
  - mediaId: ID generado por media-service
  - senderId: username del remitente
  - receiverId: username del destinatario
  - imageUrl: URL pública de la imagen
  - thumbnailUrl: URL de la miniatura (si existe)
  - timestamp: marca de tiempo
  - status: SENT
```

**Qué ocurre:**
- El cliente construye un `WsMessage` con `ImageMessage` anidado
- Se envía por WebSocket al connection-service

---

### Paso 4: Connection-service procesa ImageMessage

El `ConnectionMessageDispatcher` recibe el mensaje y delega al handler correspondiente.

**Qué ocurre:**
- Se detecta que es un `ImageMessage`
- Se llama a `MessageRouterService.routeImageMessage()`
- Se consulta Redis para determinar el estado del receptor

**Claves Redis consultadas:**
- `user:name:{username}` → userId
- `user:{userId}:connectionInstance` → instanceId

---

### Paso 5: Notificación al receptor según estado

El sistema enruta la notificación según el estado de conexión del receptor.

#### Caso A: Receptor online en la misma instancia

**Qué ocurre:**
- Se envía `ImageMessage` directamente al receptor por WebSocket
- Se envía notificación push via SSE

#### Caso B: Receptor online en otra instancia

**Qué ocurre:**
- Se publica en cola `message.sent.{instanceId}`
- La instancia destino reenvía el mensaje por WebSocket

#### Caso C: Receptor offline

**Qué ocurre:**
- Se publica en cola `message.offline`
- Se envía notificación a cola `message.notification`
- El `ImageMessage` se guarda en la base de datos (chat-service)
- Se marca como no entregado (`delivered = false`)

---

### Paso 6: Chat-service procesa mensaje offline

El `OfflineImageMessageHandler` consume el mensaje de la cola offline.

**Qué ocurre:**
1. Se guarda el `ImageMessage` en la tabla `image_messages`
2. Se crea un registro `PendingImageMessage` para entrega diferida
3. Se verifica si el remitente está bloqueado

**Tabla: image_messages**
```sql
CREATE TABLE image_messages (
    id BIGINT PRIMARY KEY AUTOINCREMENT,
    sender_id VARCHAR(255) NOT NULL,
    receiver_id VARCHAR(255) NOT NULL,
    media_id VARCHAR(255) NOT NULL,
    image_url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500),
    delivered BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

---

### Paso 7: Receptor visualiza imagen

El cliente recibe el `ImageMessage` y muestra la información.

**Qué ocurre:**
1. El cliente recibe `ImageMessage` por WebSocket
2. Se muestra una previsualización con la URL de la imagen
3. El usuario puede hacer clic para descargar la imagen completa

---

### Paso 8: Descarga de imagen

El receptor descarga la imagen desde media-service.

**Endpoint:**
```
POST /api/v1/media/download
Content-Type: application/x-protobuf
```

**Request:**
```
DownloadImageRequest:
  - mediaId: ID de la imagen
  - userId: ID del usuario que solicita
```

**Endpoint alternativo:**
```
GET /api/v1/media/download/{mediaId}
Header: X-User-Id: {userId}
```

**Respuesta:**
```
DownloadImageResponse:
  - success: true/false
  - imageData: bytes de la imagen
  - mimeType: tipo MIME (image/jpeg, image/png)
  - width: ancho en píxeles
  - height: alto en píxeles
  - errorMessage: mensaje de error si falló
```

**Qué ocurre:**
1. Se verifica que el usuario tiene permisos para descargar
2. Se leen los bytes del archivo
3. Se retornan al cliente
4. El cliente muestra la imagen completa

---

## 3. Escenario: Receptor Online

```
[Cliente A]                    [Servidor]                    [Cliente B]
     │                              │                              │
     │── 1. HTTP POST /upload ────>│                              │
     │<── 2. UploadImageResponse ───│                              │
     │                              │                              │
     │── 3. WebSocket ImageMessage ───────────────────────────────>│
     │   (mediaId, URL, sender)     │                              │
     │                              │                              │
     │                              │── SSE notification ──────────>│
     │                              │                              │
     │                              │                    4. Usuario ve previsualización
     │                              │                    5. Usuario descarga imagen
     │                              │<──────────────────────────────│
     │                              │── 6. HTTP GET /download       │
     │                              │── 7. Bytes de imagen         │
     │                              │──────────────────────────────>│
     │                              │                    8. Muestra imagen
```

**Características:**
- Entrega inmediata del mensaje
- Notificación push en tiempo real
- El receptor puede descargar la imagen inmediatamente

---

## 4. Escenario: Receptor Offline

```
[Cliente A]                    [Servidor]                    [Cliente B]
     │                              │                              │
     │── 1. HTTP POST /upload ────>│                              │
     │<── 2. UploadImageResponse ───│                              │
     │                              │                              │
     │── 3. WebSocket ImageMessage ─>│                              │
     │   (mediaId, URL, sender)     │                              │
     │                              │                              │
     │                              │── Cola message.offline ────>│
     │                              │                              │
     │                              │<── OfflineMessageConsumer   │
     │                              │── Guarda image_message en BD│
     │                              │── PendingImageMessage       │
     │                              │                              │
     │<── 4. ImageMessageResponse ──│                              │
     │                              │                              │
     │                              │   (Cliente B offline)        │
     │                              │                              │
     │                              │   B se conecta              │
     │                              │<──────────────────────────────│
     │                              │── GET /api/v1/messages/pending
     │                              │── UnreadImageMessagesList   │
     │                              │────────────────────────────>│
     │                              │                    5. Usuario ve previsualización
     │                              │                    6. Usuario descarga imagen
     │                              │<──────────────────────────────│
```

**Características:**
- Mensaje guardado en base de datos
- Entrega diferida al reconectarse
- El receptor recibe todos los mensajes de imagen pendientes en una sola respuesta

---

## 5. Recuperación de Imágenes Pendientes

Cuando un usuario se reconecta, el sistema entrega todos los mensajes de imagen pendientes.

### Proceso

1. Cliente establece conexión WebSocket con connection-service
2. Cliente solicita pendientes via REST API: `GET /api/v1/messages/pending/{username}`
3. Chat-service devuelve todos los pendientes incluyendo `UnreadImageMessagesList`
4. El cliente procesa la lista y muestra las previsualizaciones

### Mensaje: UnreadImageMessagesList

```json
{
  "unreadImageMessagesList": {
    "messages": [
      {
        "mediaId": "img-uuid-123",
        "senderId": "usuario1",
        "receiverId": "usuario2",
        "imageUrl": "https://media.example.com/img/img-uuid-123.jpg",
        "timestamp": 1709900000000,
        "status": "SENT"
      }
    ]
  }
}
```

---

## 6. Formato de Mensajes

### UploadImageRequest

```json
{
  "uploadImageRequest": {
    "userId": "user-123",
    "receiverId": "user-456",
    "imageData": "<bytes de la imagen>",
    "originalFilename": "vacaciones.jpg",
    "originalWidth": 1920,
    "originalHeight": 1080
  }
}
```

### UploadImageResponse

```json
{
  "uploadImageResponse": {
    "success": true,
    "mediaId": "img-uuid-123",
    "fullImageUrl": "https://media.example.com/images/img-uuid-123.jpg",
    "fullImageSize": 524288,
    "errorMessage": ""
  }
}
```

### ImageMessage

```json
{
  "imageMessage": {
    "mediaId": "img-uuid-123",
    "senderId": "usuario1",
    "receiverId": "usuario2",
    "imageUrl": "https://media.example.com/images/img-uuid-123.jpg",
    "thumbnailUrl": "https://media.example.com/thumbnails/img-uuid-123.jpg",
    "timestamp": 1709900000000,
    "status": "SENT"
  }
}
```

---

## 7. Estructura de Clases Principales

### media-service

| Clase | Responsabilidad |
|-------|-----------------|
| `MediaController` | Endpoints REST para upload/download |
| `MediaService` | Lógica de procesamiento y almacenamiento |
| `FileStorageService` | Gestión del sistema de archivos |

### connection-service

| Clase | Responsabilidad |
|-------|-----------------|
| `ConnectionMessageDispatcher` | Dispatcher que delega mensajes a handlers |
| `ImageMessageHandler` | Procesa mensajes de imagen del cliente |
| `MessageRouterService` | Enruta mensajes según estado de conexión |
| `NotificationEvent` | Crea eventos de notificación para imágenes |

### chat-service

| Clase | Responsabilidad |
|-------|-----------------|
| `ImageMessageRepository` | Acceso a tabla image_messages |
| `OfflineImageMessageHandler` | Procesa mensajes de imagen offline |
| `MessageService` | Gestiona recuperación de pendientes |

### websocket-client

| Clase | Responsabilidad |
|-------|-----------------|
| `ImageService` | Maneja subida y descarga de imágenes |
| `IncomingMessageProcessor` | Procesa ImageMessage recibidos |
| `processUnreadImageMessages` | Maneja lista de imágenes pendientes |

---

## 8. Diagrama del Flujo Completo

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        ENVÍO DE IMAGEN (RECEPTOR ONLINE)                    │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente A]                     [media-service]              [connection-service]           [Cliente B]
     │                               │                              │                           │
     │── HTTP POST /upload ────────>│                              │                           │
     │   (UploadImageRequest)       │                              │                           │
     │                              │── Genera mediaId            │                           │
     │                              │── Guarda archivo            │                           │
     │<── UploadImageResponse ──────│── (mediaId, URL)            │                           │
     │   (mediaId, fullImageUrl)    │                              │                           │
     │                              │                              │                           │
     │── WebSocket ImageMessage ─────────────────────────────────────>│                           │
     │   (WsMessage con ImageMessage)                               │                           │
     │                              │                              │── ImageMessageHandler     │
     │                              │                              │── routeImageMessage()      │
     │                              │                              │                           │
     │                              │                              │── ¿B online? ──┐          │
     │                              │                              │              │          │
     │                              │                              │              ▼          │
     │                              │                              │     [Online - misma inst] │
     │                              │                              │     WebSocket directo ───>│
     │                              │                              │                           │── handleImageMessage()
     │                              │                              │                           │── Muestra previsualización
     │                              │                              │                           │
     │                              │                              │              │          │
     │                              │                              │<──────── [Otra inst]     │
     │                              │                              │── Cola message.sent ────>│
     │                              │                              │                           │
     │                              │                              │── Cola notification ────>│ notification-service
     │                              │                              │                           │── SSE push ──────────>│
     │                              │                              │                           │
     │<── ImageMessageResponse ─────│                              │                           │
     │                              │                              │                           │
     │                              │                              │                           │── Usuario hace clic
     │                              │                              │                           │── HTTP GET /download
     │                              │<─────────────────────────────────────────────────────────│
     │                              │── imageData (bytes)         │                           │
     │                              │───────────────────────────────>                           │
     │                              │                              │                           │── Muestra imagen completa


┌─────────────────────────────────────────────────────────────────────────────┐
│                       ENVÍO DE IMAGEN (RECEPTOR OFFLINE)                     │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente A]                     [media-service]              [connection-service]           [Cliente B]
     │                               │                              │                           │
     │── HTTP POST /upload ────────>│                              │                           │
     │                              │── Guarda, genera mediaId    │                           │
     │<── UploadImageResponse ──────│                              │                           │
     │                              │                              │                           │
     │── WebSocket ImageMessage ─────────────────────────────────────>│                           │
     │                              │                              │                           │
     │                              │                              │── routeImageMessage()      │
     │                              │                              │── ¿B online? ──┐          │
     │                              │                              │              │          │
     │                              │                              │              ▼          │
     │                              │                              │     [Offline]            │
     │                              │                              │── Cola message.offline ─>│
     │                              │                              │                           │
     │<── ImageMessageResponse ─────│                              │                           │
     │                              │                              │                           │
     │                              │                    chat-service                        │
     │                              │<──────────────────────────────│                           │
     │                              │── OfflineMessageConsumer     │                           │
     │                              │── ImageMessageRepository     │                           │
     │                              │── save(imageMessage)         │                           │
     │                              │                              │                           │
     │                              │   (Cliente B se conecta)     │                           │
     │                              │                              │<──────────────────────────│
     │                              │                              │── GET /pending ─────────>│
     │                              │                              │<── UnreadImageMessagesList│
     │                              │                              │──────────────────────────>│
     │                              │                              │                           │── handleImageMessage()
     │                              │                              │                           │── Muestra previsualización
     │                              │                              │                           │
     │                              │                              │                           │── Usuario descarga
     │                              │<─────────────────────────────────────────────────────────│
     │                              │── imageData                 │                           │
     │                              │───────────────────────────────>                           │
     │                              │                              │                           │── Muestra imagen
```

---

## 9. Resumen

El flujo de envío y recepción de imágenes garantiza que:

1. **Subida eficiente**: Las imágenes se suben al media-service y se almacenan en el sistema de archivos
2. **Notificación inmediata**: El receptor recibe el mensaje en tiempo real si está online
3. **Entrega offline**: Los mensajes de imagen se guardan y entregan al reconectarse
4. **Descarga bajo demanda**: El receptor decide cuándo descargar la imagen completa
5. **Verificación de permisos**: Solo usuarios autorizados pueden descargar imágenes
6. **Recuperación de pendientes**: Al reconectarse, el usuario recibe todos los mensajes de imagen pendientes en una sola respuesta
