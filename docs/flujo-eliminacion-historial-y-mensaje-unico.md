# Flujo de Eliminación de Mensaje Único e Historial

Este documento describe el flujo completo de las operaciones de eliminación de mensajes individuales y limpieza de historial de chat. El sistema permite a los usuarios eliminar mensajes específicos o borrar todo el historial de conversación, con soporte para entrega en tiempo real y diferida.

---

## 1. Arquitectura General

### Servicios Involucrados

| Servicio | Puerto | Responsabilidad |
|----------|--------|-----------------|
| connection-service | 8083 | Recibe solicitudes WebSocket, notifica al destinatario |
| chat-service | 8085 | Elimina mensajes, gestiona notificaciones pendientes |
| notification-service | 8084 | Envía notificaciones push mediante SSE |

### Colas de RabbitMQ

El sistema utiliza el exchange `message.exchange` con las siguientes colas:

| Cola | Routing Key | Propósito | Productor | Consumidor |
|------|-------------|-----------|-----------|------------|
| `message.delete` | `delete` | Solicitudes de eliminación de mensaje | connection-service | chat-service |
| `message.sent.{instanceId}` | `{instanceId}` | Solicitudes para usuarios online en instancia específica | connection-service | chat-service |
| `message.offline` | `offline` | Solicitudes para usuarios offline | connection-service | chat-service |
| `message.delivery` | `delivery` | Estados de entrega (DELIVERED, READ, MESSAGE_DELETED) | chat-service | connection-service |

### Tablas de Base de Datos

| Tabla | Propósito |
|-------|-----------|
| `messages` | Almacena todos los mensajes del chat |
| `pending_deletions` | Registros de eliminación de mensajes pendientes de notificar |
| `pending_clear_histories` | Solicitudes de limpieza de historial pendientes |

### Canales de Comunicación

- **WebSocket**: Comunicación bidireccional entre cliente y connection-service
- **RabbitMQ**: Mensajería asíncrona entre servicios
- **REST API**: Recuperación de mensajes pendientes

---

## 2. Flujo de Eliminación de Mensaje Único

### Concepto

La eliminación de mensaje único permite a un usuario eliminar un mensaje específico que envió, y esta eliminación se refleja en el destinatario si está online ("para todos").

### Paso 1: Cliente envía DeleteMessageRequest

El cliente JavaFX envía una solicitud de eliminación a través de WebSocket.

**Datos del mensaje:**
```
DeleteMessageRequest:
  - messageId: ID único del mensaje a eliminar
  - senderUsername: username del propietario del mensaje
```

---

### Paso 2: Llega a connection-service

El `ConnectionMessageDispatcher` recibe el mensaje y delega a `DeleteMessageHandler`.

**Qué ocurre:**
- `DeleteMessageHandler` detecta `DeleteMessageRequest`
- Extrae el messageId y senderUsername
- Llama a `messageRouterService.routeDeletionRequest()`

---

### Paso 3: MessageRouterService envía a cola delete

El `routeDeletionRequest` publica el mensaje en la cola `message.delete`.

**Qué ocurre:**
- Se serializa el mensaje completo
- Se publica en la cola `message.delete` via `RabbitMQProducerService`
- El mensaje será consumido por chat-service

---

### Paso 4: chat-service procesa la eliminación

El `DeleteMessageConsumer` consume el mensaje de la cola `message.delete`.

**Qué ocurre:**
1. Se elimina el mensaje de la tabla `messages` en la base de datos
2. Se crea un registro `PendingDeletion` para notificar al destinatario
3. Se envía notificación `MESSAGE_DELETED` a la cola `message.delivery`

---

### Paso 5: connection-service recibe estado de entrega

El `DeliveryStatusConsumer` recibe el evento de `MESSAGE_DELETED`.

**Qué ocurre:**
- Se extrae el messageId del evento
- Se determina el estado de conexión del destinatario original
- Si está online: se envía `MessageDeletedNotification` por WebSocket
- Si está offline: la notificación queda pendiente para cuando se conecte

---

### Paso 6: Destinatario recibe notificación

El cliente recibe `MessageDeletedNotification` por WebSocket.

**Datos recibidos:**
```
MessageDeletedNotification:
  - messageId: ID del mensaje eliminado
  - deletedBy: username del usuario que eliminó
```

**Qué ocurre:**
- El cliente elimina el mensaje de su base de datos local
- Se actualiza la UI para reflejar la eliminación

---

## 3. Flujo de Eliminación de Historial

### Concepto

La eliminación de historial permite borrar toda la conversación con un contacto. El usuario puede elegir:
- **Solo para mí**: Elimina los mensajes de la base de datos local únicamente
- **Para todos**: Elimina los mensajes en el servidor y notifica al destinatario

### Paso 1: Cliente envía ClearHistoryRequest

El cliente JavaFX envía una solicitud de limpieza de historial por WebSocket.

**Datos del mensaje:**
```
ClearHistoryRequest:
  - sender: username del usuario que solicita la eliminación
  - recipient: username del contacto con quien eliminar historial
```

---

### Paso 2: Llega a connection-service

El `ConnectionMessageDispatcher` recibe el mensaje y delega a `ClearHistoryHandler`.

**Qué ocurre:**
- `ClearHistoryHandler` detecta `ClearHistoryRequest`
- Extrae sender y recipient
- Llama a `messageRouterService.routeClearHistoryRequest()`

---

### Paso 3: MessageRouterService procesa según estado del destinatario

#### Caso A: Destinatario online en la misma instancia

**Qué ocurre:**
- Se envía `ClearHistoryRequest` directamente al destinatario por WebSocket
- El destinatario elimina su historial localmente
- chat-service elimina los mensajes de la base de datos del servidor

#### Caso B: Destinatario online en otra instancia

**Qué ocurre:**
- Se publica en la cola `message.sent.{instanceId}`
- chat-service de la instancia destino recibe y reenvía
- connection-service de la instancia destino entrega al destinatario

#### Caso C: Destinatario offline

**Qué ocurre:**
- Se publica en la cola `message.offline`
- `OfflineClearHistoryHandler` procesa la solicitud:
  - Elimina todos los mensajes entre ambos usuarios de la BD
  - Guarda `PendingClearHistory` para entregar cuando se conecte

---

### Paso 4: Eliminación en chat-service

#### Para destinatario online

El `OnlineClearHistoryHandler` procesa la solicitud.

**Qué ocurre:**
1. Elimina todos los mensajes de la BD entre sender y recipient (ambas direcciones)
2. Envía confirmación al cliente que solicitó

#### Para destinatario offline

El `OfflineClearHistoryHandler` procesa la solicitud.

**Qué ocurre:**
1. Elimina todos los mensajes de la BD entre sender y recipient
2. Guarda `PendingClearHistory` en la base de datos
3. El destinatario recibirá la solicitud al reconectarse

---

### Paso 5: Destinatario procesa la solicitud

#### Tiempo real (online)

El cliente recibe `ClearHistoryRequest` y:
1. Elimina todos los mensajes con ese contacto de su SQLite local
2. Limpia la UI si está viendo ese chat

#### Offline (pendiente)

El cliente recibe `PendingClearHistoryList` y para cada entrada:
1. Verifica que sea para este usuario
2. Elimina los mensajes locales
3. Actualiza la UI

---

## 4. Eliminación Solo para Mí

### Concepto

Cuando el usuario elige "Solo para mí", la eliminación solo afecta su base de datos local.

### Flujo

1. Usuario presiona "Vaciar Chat" → "Solo para mí"
2. El cliente elimina directamente de SQLite:
   ```sql
   DELETE FROM messages WHERE contact_username = ?
   ```
3. Se actualiza la UI
4. NO se envía solicitud al servidor
5. El otro usuario conserva sus mensajes

---

## 5. Recuperación de Pendientes al Conectarse

Cuando un usuario se reconecta, el sistema entrega todas las notificaciones pendientes.

### Proceso

1. Cliente establece conexión WebSocket con connection-service
2. Cliente solicita pendientes via REST API: `GET /api/v1/messages/pending/{username}`
3. Chat-service devuelve todos los pendientes en un solo `WsMessage`
4. El cliente procesa todos los tipos de pendientes

### Tipos de Pendientes Entregados

| Tipo | Descripción |
|------|-------------|
| `UnreadMessagesList` | Mensajes de chat no leídos |
| `PendingDeletions` | Eliminaciones de mensajes específicos |
| `PendingClearHistoryList` | Limpiezas de historial |
| `BlockedUsersList` | Usuarios bloqueados |
| `UnblockedUsersList` | Usuarios desbloqueados |
| `MessagesReadUpdate` | Confirmaciones de lectura |

---

## 6. Formato de Mensajes

### DeleteMessageRequest

```json
{
  "deleteMessageRequest": {
    "messageId": "msg-123",
    "senderUsername": "usuario1"
  }
}
```

### MessageDeletedNotification

```json
{
  "messageDeletedNotification": {
    "messageId": "msg-123",
    "deletedBy": "usuario1"
  }
}
```

### ClearHistoryRequest

```json
{
  "clearHistoryRequest": {
    "sender": "usuario1",
    "recipient": "usuario2"
  }
}
```

### PendingClearHistory

```json
{
  "pendingClearHistory": {
    "sender": "usuario1",
    "recipient": "usuario2"
  }
}
```

---

## 7. Estructura de Clases Principales

### connection-service

| Clase | Responsabilidad |
|-------|-----------------|
| `ConnectionMessageDispatcher` | Dispatcher que delega mensajes a handlers específicos |
| `DeleteMessageHandler` | Procesa solicitudes de eliminación de mensaje |
| `ClearHistoryHandler` | Procesa solicitudes de limpieza de historial |
| `MessageRouterService` | Enruta solicitudes según estado de conexión |
| `DeliveryStatusConsumer` | Consume estados de entrega desde message.delivery |

### chat-service

| Clase | Responsabilidad |
|-------|-----------------|
| `DeleteMessageConsumer` | Consume solicitudes de eliminación de mensaje |
| `OfflineClearHistoryHandler` | Procesa limpieza de historial para usuarios offline |
| `MessageRepository` | Acceso a tabla messages |
| `PendingDeletionRepository` | Acceso a tabla pending_deletions |
| `PendingClearHistoryRepository` | Acceso a tabla pending_clear_histories |

### websocket-client

| Clase | Responsabilidad |
|-------|-----------------|
| `MessageService` | Coordina eliminación local y envío al servidor |
| `MessageRepository` | Acceso a tabla messages en SQLite |
| `IncomingMessageProcessor` | Procesa notificaciones recibidas del servidor |

---

## 8. Diagrama del Flujo: Eliminación de Mensaje Único

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   ELIMINACIÓN DE MENSAJE ÚNICO                              │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente A]                         [Servidor]                    [Cliente B]
     │                                  │                              │
     │── DeleteMessageRequest ─────────>│                              │
     │   (messageId, senderUsername)    │                              │
     │                                  │                              │
     │                                  │── DeleteMessageHandler       │
     │                                  │── routeDeletionRequest()      │
     │                                  │                              │
     │                                  │── Cola message.delete ──────>│
     │                                  │                              │
     │                                  │<── DeleteMessageConsumer     │
     │                                  │── Elimina de BD              │
     │                                  │── PendingDeletion            │
     │                                  │── Cola message.delivery      │
     │                                  │                              │
     │<── DeleteMessageResponse ────────│                              │
     │                                  │                              │
     │                                  │── Cola message.delivery ────>│
     │                                  │   (MESSAGE_DELETED)          │
     │                                  │                              │
     │                                  │<── DeliveryStatusConsumer    │
     │                                  │── B online? ──┐              │
     │                                  │              │              │
     │                                  │              ▼              │
     │                                  │         [Si online]          │
     │                                  │         WebSocket ─────────>│
     │                                  │                              │── MessageDeletedNotification
     │                                  │                              │── Elimina de SQLite local
     │                                  │                              │── Actualiza UI
     │                                  │                              │
     │                                  │              │              │
     │                                  │<──────── [Si offline]        │
     │                                  │── PendingDeletion ────────>│
     │                                  │                              │ (al reconectarse)


┌─────────────────────────────────────────────────────────────────────────────┐
│               ELIMINACIÓN DE MENSAJE (DESTINATARIO OFFLINE)                  │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente A]                         [Servidor]
     │                                  │
     │── DeleteMessageRequest ─────────>│
     │                                  │── routeDeletionRequest()
     │                                  │── Cola message.delete
     │                                  │
     │<── DeleteMessageResponse ────────│
     │                                  │
     │                                  │── DeleteMessageConsumer
     │                                  │── Elimina de BD
     │                                  │── PendingDeletion
     │                                  │
     │                                  │ (B está offline)
     │                                  │── PendingDeletion guardado
     │                                  │   para entregar cuando
     │                                  │   B se conecte
```

---

## 9. Diagrama del Flujo: Eliminación de Historial

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                   ELIMINACIÓN DE HISTORIAL "PARA TODOS"                     │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente A]                         [Servidor]                    [Cliente B]
     │                                  │                              │
     │── ClearHistoryRequest ─────────>│                              │
     │   (sender=A, recipient=B)       │                              │
     │                                  │                              │
     │                                  │── ClearHistoryHandler        │
     │                                  │── routeClearHistoryRequest() │
     │                                  │                              │
     │                                  │── B online? ──┐              │
     │                                  │              │              │
     │                                  │              ▼              │
     │                                  │         [Si online]          │
     │                                  │         Cola message.sent ──>│
     │                                  │                              │
     │                                  │<── ChatMessageConsumer       │
     │                                  │── OnlineClearHistoryHandler  │
     │                                  │── Elimina mensajes BD        │
     │                                  │   (A→B y B→A)               │
     │                                  │                              │
     │                                  │         WebSocket ─────────>│
     │                                  │                              │── ClearHistoryRequest
     │                                  │                              │── Elimina SQLite local
     │                                  │                              │── Limpia UI
     │                                  │                              │
     │                                  │              │              │
     │                                  │<──────── [Si offline]        │
     │                                  │── Cola message.offline ────>│
     │                                  │                              │
     │                                  │<── OfflineMessageConsumer    │
     │                                  │── OfflineClearHistoryHandler │
     │                                  │── Elimina mensajes BD        │
     │                                  │── PendingClearHistory        │
     │                                  │                              │
     │<── ClearHistoryResponse ────────│                              │
     │                                  │                              │ (al reconectarse)


┌─────────────────────────────────────────────────────────────────────────────┐
│                   ELIMINACIÓN DE HISTORIAL "SOLO PARA MÍ"                   │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente A]                         [Base de Datos Local]
     │                                  │
     │── Usuario presiona ─────────────>│
     │   "Vaciar Chat"                  │
     │   → "Solo para mí"              │
     │                                  │
     │── DELETE FROM messages ─────────>│
     │   WHERE contact_username = ?     │
     │                                  │
     │── Mensajes eliminados ──────────>│
     │   de SQLite local                │
     │                                  │
     │── UI actualizada ──────────────>│
     │   (chat limpio)                 │
     │                                  │
     │   NO se envía solicitud          │
     │   al servidor                    │
     │                                  │
     │                                  │ Cliente B conserva
     │                                  │ todos sus mensajes
```

---

## 10. Resumen

El flujo de eliminación garantiza que:

1. **Eliminación de mensaje único**: Un mensaje específico se elimina del servidor y se notifica al destinatario
2. **Eliminación de historial**: Toda la conversación se elimina de ambos lados
3. **Modo "solo para mí"**: El usuario puede eliminar solo su copia local sin afectar al otro
4. **Entrega offline**: Las notificaciones de eliminación se guardan y entregan al reconectarse
5. **Consistencia**: El sistema mantiene coherencia entre cliente y servidor mediante pendientes
