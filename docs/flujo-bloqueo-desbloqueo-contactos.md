# Flujo de Bloqueo y Desbloqueo de Contactos

Este documento describe el flujo completo de las operaciones de bloqueo y desbloqueo de contactos, incluyendo el mecanismo de arrepentimiento. El sistema permite a los usuarios bloquear contactos para evitar recibir mensajes, con persistencia en base de datos y entrega de notificaciones en tiempo real o diferidas.

---

## 1. Arquitectura General

### Servicios Involucrados

| Servicio | Puerto | Responsabilidad |
|----------|--------|-----------------|
| connection-service | 8083 | Recibe solicitudes WebSocket, notifica al destinatario online |
| chat-service | 8085 | Registra bloqueos permanentes, gestiona notificaciones pendientes |
| profile-service | 8088 | Gestión de usuarios y autenticación |

### Colas de RabbitMQ

El sistema utiliza el exchange `message.exchange` con las siguientes colas:

| Cola | Routing Key | Propósito | Productor | Consumidor |
|------|-------------|-----------|-----------|------------|
| `message.sent.{instanceId}` | `{instanceId}` | Solicitudes para usuarios online en instancia específica | connection-service | chat-service |
| `message.offline` | `offline` | Solicitudes para usuarios offline | connection-service | chat-service |

### Tablas de Base de Datos

| Tabla | Propósito |
|-------|-----------|
| `contact_blocks` | Registros permanentes de bloqueo (blocker → blocked) |
| `pending_blocks` | Solicitudes de bloqueo pendientes de notificar |
| `pending_unblocks` | Solicitudes de desbloqueo pendientes de notificar |

### Canales de Comunicación

- **WebSocket**: Comunicación bidireccional entre cliente y connection-service
- **RabbitMQ**: Mensajería asíncrona entre servicios
- **REST API**: Recuperación de notificaciones pendientes

---

## 2. Flujo Detallado de Bloqueo

### Paso 1: Cliente envía BlockContactRequest

El cliente JavaFX envía una solicitud de bloqueo a través de WebSocket.

**Datos del mensaje:**
```
BlockContactRequest:
  - blocker: username del usuario que bloquea
  - recipient: username del usuario a bloquear
```

---

### Paso 2: Llega a connection-service

El `ConnectionMessageDispatcher` recibe el mensaje y delega a `BlockContactHandler`.

**Qué ocurre:**
- `BlockContactHandler` detecta `BlockContactRequest`
- Extrae el username del destinatario (recipient)
- Llama a `messageRouterService.routeBlockUnblockToChatService()`

---

### Paso 3: MessageRouterService procesa en dos flujos

El `routeBlockUnblockToChatService` ejecuta dos acciones en paralelo:

#### Flujo A: Registro permanente en chat-service

**Qué ocurre:**
- Consulta Redis para obtener el estado del destinatario
- Si está offline: envía a cola `message.offline`
- Si está online en otra instancia: envía a cola `message.sent.{instanceId}`

**Componentes involucrados:**
- `SessionRegistryService`: Consulta estado de conexión en Redis
- `RabbitMQProducerService`: Publica a colas de RabbitMQ

#### Flujo B: Notificación directa al destinatario

**Qué ocurre:**
- Si el destinatario está online en la misma instancia: WebSocket directo
- Si está online en otra instancia: RabbitMQ a esa instancia
- Si está offline: no se envía (se entrega al reconectarse)

---

### Paso 4: chat-service procesa según estado del destinatario

#### Caso A: Destinatario online

El `OnlineBlockContactHandler` procesa la solicitud.

**Qué ocurre:**
- Registra el bloqueo en `contact_blocks` via `BlockService.blockUser()`
- No guarda en `pending_blocks` (el destinatario ya recibe la notificación directa)

**Código clave:**
```java
boolean blockRegistered = blockService.blockUser(blocker, blockedUser);
```

---

#### Caso B: Destinatario offline

El `OfflineBlockContactHandler` procesa la solicitud.

**Qué ocurre:**
1. Registra el bloqueo en `contact_blocks` via `BlockService.blockUser()`
2. Verifica si existe `PendingUnblock` previo (lógica de arrepentimiento)
3. Si existe, lo elimina
4. Guarda la notificación en `pending_blocks`

**Lógica de arrepentimiento:**
- Si el usuario ya tenía un desbloqueo pendiente, se elimina
- El nuevo bloqueo tiene prioridad

---

## 3. Flujo Detallado de Desbloqueo

### Paso 1: Cliente envía UnblockContactRequest

El cliente envía la solicitud por WebSocket.

**Datos del mensaje:**
```
UnblockContactRequest:
  - blocker: username del usuario que desbloquea
  - recipient: username del usuario a desbloquear
```

---

### Paso 2: Llega a connection-service

El `ConnectionMessageDispatcher` delega a `UnblockContactHandler`.

**Qué ocurre:**
- Extrae el username del destinatario
- Llama a `routeBlockUnblockToChatService()`

---

### Paso 3: MessageRouterService procesa en dos flujos

Igual que en el flujo de bloqueo:
1. Registro permanente en chat-service
2. Notificación directa al destinatario (si está online)

---

### Paso 4: chat-service procesa según estado

#### Caso A: Destinatario online

El `OnlineUnblockContactHandler` procesa la solicitud.

**Qué ocurre:**
- Elimina el registro de `contact_blocks` via `BlockService.unblockUser()`

---

#### Caso B: Destinatario offline

El `OfflineUnblockContactHandler` procesa la solicitud.

**Qué ocurre:**
1. Elimina el bloqueo de `contact_blocks`
2. Verifica si existe `PendingBlock` previo (lógica de arrepentimiento)
3. Si existe, lo elimina
4. Guarda la notificación en `pending_unblocks`

---

## 4. Flujo de Arrepentimiento

El sistema implementa un mecanismo de arrepentimiento que permite a los usuarios cambiar de opinión antes de que el destinatario reciba la notificación.

### Escenario: Bloqueo después de desbloqueo pendiente

```
Usuario A (online)                    Servidor                    Usuario B (offline)
     │                                    │                              │
     │── UnblockContactRequest ─────────>│                              │
     │                                    ├── contact_blocks (elimina)  │
     │                                    ├── pending_unblocks (crea)    │
     │<── UnblockContactResponse ────────│                              │
     │                                    │                              │
     │── BlockContactRequest ────────────>│  (arrepentimiento)          │
     │                                    │                              │
     │                                    ├── pending_unblocks (elimina) │
     │                                    ├── contact_blocks (crea)      │
     │                                    ├── pending_blocks (crea)     │
     │<── BlockContactResponse ──────────│                              │
```

**Qué ocurre:**
- Se elimina el `PendingUnblock` que estaba pendiente
- Se crea el registro en `contact_blocks`
- Se guarda un nuevo `PendingBlock` para notificar a B cuando se conecte

### Escenario: Desbloqueo después de bloqueo pendiente

```
Usuario A (online)                    Servidor                    Usuario B (offline)
     │                                    │                              │
     │── BlockContactRequest ────────────>│                              │
     │                                    ├── contact_blocks (crea)      │
     │                                    ├── pending_blocks (crea)     │
     │<── BlockContactResponse ──────────│                              │
     │                                    │                              │
     │── UnblockContactRequest ─────────>│  (arrepentimiento)           │
     │                                    │                              │
     │                                    ├── pending_blocks (elimina)   │
     │                                    ├── contact_blocks (elimina)    │
     │                                    ├── pending_unblocks (crea)    │
     │<── UnblockContactResponse ────────│                              │
```

**Qué ocurre:**
- Se elimina el `PendingBlock` que estaba pendiente
- Se elimina el registro de `contact_blocks`
- Se guarda un nuevo `PendingUnblock` para notificar a B cuando se conecte

---

## 5. Recuperación de Pendientes al Conectarse

Cuando un usuario se reconecta, el sistema entrega todas las notificaciones pendientes.

### Proceso

1. Cliente establece conexión WebSocket y SSE
2. Cliente solicita pendientes via REST API: `GET /api/v1/messages/pending/{username}`
3. Chat-service devuelve todos los pendientes incluyendo:
   - `PendingBlock`: El usuario fue bloqueado por otro
   - `PendingUnblock`: El usuario fue desbloqueado por otro
4. Cliente actualiza su base de datos local
5. Los pendientes se eliminan de la base de datos

### Componentes involucrados

| Clase | Responsabilidad |
|-------|-----------------|
| `MessageService.getAndClearPendingBlocks()` | Recupera y elimina pending_blocks |
| `MessageService.getAndClearPendingUnblocks()` | Recupera y elimina pending_unblocks |

---

## 6. Verificación de Bloqueo al Enviar Mensajes

Cuando un usuario intenta enviar un mensaje, el sistema verifica si el destinatario lo ha bloqueado.

### Flujo

1. Mensaje llega a `OfflineChatMessageHandler`
2. Se consulta `BlockService.isBlocked(sender, recipient)`
3. Si está bloqueado: el mensaje se descarta y se registra un log
4. Si no está bloqueado: el mensaje se guarda en la base de datos

**Código clave:**
```java
public boolean isBlocked(String sender, String recipient) {
    return blockRepository.existsByBlockerAndBlocked(recipient, sender);
}
```

---

## 7. Formato de Mensajes

### BlockContactRequest

```json
{
  "blockContactRequest": {
    "blocker": "usuario1",
    "recipient": "usuario2"
  }
}
```

### UnblockContactRequest

```json
{
  "unblockContactRequest": {
    "blocker": "usuario1",
    "recipient": "usuario2"
  }
}
```

### BlockContactResponse

```json
{
  "blockContactResponse": {
    "success": true,
    "message": "Usuario bloqueado exitosamente"
  }
}
```

### UnblockContactResponse

```json
{
  "unblockContactResponse": {
    "success": true,
    "message": "Usuario desbloqueado exitosamente"
  }
}
```

---

## 8. Estructura de Clases Principales

### connection-service

| Clase | Responsabilidad |
|-------|-----------------|
| `ConnectionMessageDispatcher` | Dispatcher que delega mensajes a handlers específicos |
| `BlockContactHandler` | Procesa solicitudes de bloqueo del cliente WebSocket |
| `UnblockContactHandler` | Procesa solicitudes de desbloqueo del cliente WebSocket |
| `MessageRouterService` | Enruta solicitudes y notifica al destinatario |
| `SessionRegistryService` | Gestiona sesiones WebSocket y consulta Redis |
| `RabbitMQProducerService` | Publica mensajes a RabbitMQ |

### chat-service

| Clase | Responsabilidad |
|-------|-----------------|
| `OnlineBlockContactHandler` | Registra bloqueos para usuarios online |
| `OnlineUnblockContactHandler` | Elimina bloqueos para usuarios online |
| `OfflineBlockContactHandler` | Registra bloqueos y pending_blocks para offline |
| `OfflineUnblockContactHandler` | Elimina bloqueos y guarda pending_unblocks |
| `BlockService` | Lógica de negocio para bloqueos/desbloqueos |
| `ContactBlockRepository` | Acceso a tabla contact_blocks |
| `PendingBlockRepository` | Acceso a tabla pending_blocks |
| `PendingUnblockRepository` | Acceso a tabla pending_unblocks |

---

## 9. Diagrama del Flujo Completo

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         BLOQUEO DE CONTACTO                                 │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente A (bloqueador)]
        │
        │ WebSocket (BlockContactRequest)
        ▼
┌──────────────────────┐
│ connection-service   │
│                      │
│ BlockContactHandler  │
│ MessageRouterService │
└──────────────────────┘
        │
        ├─────────────────────────────┬─────────────────────────────┐
        │                             │                             │
        ▼                             ▼                             ▼
┌──────────────┐           ┌─────────────────┐           ┌─────────────────┐
│ chat-service │           │ Usuario B       │           │                 │
│ Registra en  │           │ online?         │           │                 │
│ contact_blocks           └────────┬────────┘           │                 │
└──────────────┘                    │                     │                 │
        │                    ┌───────┴───────┐              │                 │
        │                    │               │              │                 │
        │                    ▼               ▼              │                 │
        │             ┌───────────┐   ┌───────────┐        │                 │
        │             │   ONLINE  │   │  OFFLINE  │        │                 │
        │             └─────┬─────┘   └─────┬─────┘        │                 │
        │                   │               │              │                 │
        │                   ▼               ▼              │                 │
        │             ┌───────────┐   ┌───────────┐        │                 │
        │             │ Notificar │   │ Registrar │        │                 │
        │             │ por WS    │   │ en        │        │                 │
        │             └───────────┘   │ pending_  │        │                 │
        │                             │ blocks    │        │                 │
        │                             └───────────┘        │                 │
        │                                    │              │                 │
        │                                    ▼              │                 │
        │                             [Cliente B          │                 │
        │                              recibe al         │                 │
        │                              reconectarse]      │                 │
        │                                                  │                 │
        └──────────────────────────────────────────────────┘
                              │
                              ▼
                     ┌───────────────┐
                     │ Cliente A      │
                     │ recibe         │
                     │ BlockContact   │
                     │ Response       │
                     └───────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                    FLUJO DE ARREPENTIMIENTO                                  │
└─────────────────────────────────────────────────────────────────────────────┘

[Usuario A]                    [Servidor]                    [Usuario B]
     │                              │                              │
     │── UnblockContact ──────────>│                              │
     │                              │── UnblockService.unblock()  │
     │                              │── PendingUnblockRepository   │
     │<── Response ────────────────│                              │
     │                              │                              │
     │  (cambia de opinión)         │                              │
     │                              │                              │
     │── BlockContact ─────────────>│                              │
     │                              │── Elimina PendingUnblock     │
     │                              │── BlockService.block()       │
     │                              │── PendingBlockRepository     │
     │<── Response ────────────────│                              │
     │                              │                              │
     │                              │── (al reconectarse B)       │
     │                              │── Notifica BlockContact      │
     │                              │── Elimina PendingBlock       │
     │                              │── Notifica a B              │


┌─────────────────────────────────────────────────────────────────────────────┐
│                    VERIFICACIÓN AL ENVIAR MENSAJE                            │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente A]              [connection-service]         [chat-service]
     │                         │                           │
     │── ChatMessage ─────────>│                           │
     │                         │── routeToChatService() ──>│
     │                         │                           │── BlockService.isBlocked()
     │                         │                           │   (A == blocker, B == recipient?)
     │                         │                           │
     │                         │                           │── Si bloqueado:
     │                         │                           │   Descarta mensaje, log
     │                         │                           │
     │                         │                           │── Si no bloqueado:
     │                         │                           │   Guarda en messages
     │                         │                           │
```
