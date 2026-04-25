# Flujo de Confirmación de Lectura de Mensajes

Este documento describe el flujo completo de confirmación de lectura de mensajes, desde que un usuario abre un chat o recibe mensajes mientras lo tiene abierto, hasta que el emisor original recibe la notificación de que sus mensajes fueron leídos. El sistema implementa un mecanismo de debounce para evitar el efecto rebote cuando llegan múltiples mensajes en rápida sucesión.

---

## 1. Arquitectura General

### Servicios Involucrados

| Servicio | Puerto | Responsabilidad |
|----------|--------|-----------------|
| websocket-client | N/A | Cliente JavaFX que maneja la UI y lógica de marcado de mensajes como leídos |
| connection-service | 8083 | Recibe y enruta las solicitudes de confirmación de lectura |
| chat-service | 8085 | Procesa confirmaciones de lectura y las persiste/redirige al emisor |

### Mensajes Protobuf

| Mensaje | Dirección | Propósito |
|---------|-----------|-----------|
| `MarkMessagesAsReadRequest` | Cliente → Servidor | Solicita marcar mensajes como leídos |
| `MessagesReadUpdate` | Servidor → Cliente (emisor) | Notifica al emisor que sus mensajes fueron leídos por un contacto |
| `MessagesReadUpdateList` | Servidor → Cliente (emisor) | Lista de actualizaciones de lectura (soporta múltiples readers) |
| `MessageDeliveredUpdate` | Servidor → Cliente (emisor) | Notifica al emisor que sus mensajes fueron entregados al destinatario |

### Canales de Comunicación

- **WebSocket**: Comunicación bidireccional para enviar confirmaciones y recibir actualizaciones en tiempo real
- **RabbitMQ**: Cola para enrutar confirmaciones entre instancias del connection-service y chat-service

---

## 2. Estados de los Mensajes

El sistema maneja un ciclo de vida completo para los mensajes enviados:

| Estado | Descripción | Indicador Visual |
|--------|--------------|------------------|
| `PENDING` | Mensaje en proceso de envío local | Círculo dorado animado |
| `SENT` | Mensaje llegó a connection-service | Círculo dorado estático |
| `DELIVERED` | Mensaje llegó al destinatario | Círculo verde |
| `READ` | Mensaje fue leído por el destinatario | Círculo verde con check |
| `FAILED` | Error en el envío | Círculo rojo |

### Flujo de Estados

```
[Cliente envía mensaje]
       ↓
   PENDING (guardado en BD local)
       ↓
[connection-service confirma recepción]
       ↓
   SENT (actualizado en BD local)
       ↓
[chat-service entrega al destinatario]
       ↓
   DELIVERED (actualizado en BD local)
       ↓
[Destinatario lee el mensaje]
       ↓
   READ (actualizado en BD local)
```

---

## 3. Escenarios del Flujo

El sistema maneja tres escenarios principales para la confirmación de lectura:

### Escenario 1: Usuario abre un chat

Cuando un usuario presiona sobre un contacto para abrir el chat, el sistema:
1. Carga el historial de mensajes del contacto
2. Identifica los mensajes no leídos en la base de datos local (status = DELIVERED o SENT)
3. Los marca como leídos localmente (status = READ)
4. Envía la lista de IDs al servidor para notificar al emisor original

### Escenario 2: Llega mensaje mientras el chat está abierto

Cuando un mensaje llega y el usuario tiene el chat abierto:
1. El mensaje se muestra en la UI inmediatamente
2. Se inicia un timer de debounce (3 segundos)
3. Si llegan más mensajes, el timer se resetea
4. Al expirar el timer, se envían todos los IDs de mensajes no leídos del contacto

### Escenario 3: Emisor original offline

Cuando el receptor lee mensajes pero el emisor original no está conectado:
1. La confirmación se procesa normalmente hasta connection-service
2. Se detecta que el emisor está offline
3. El mensaje se almacena como pendiente en chat-service (`pending_read_receipts`)
4. Cuando el emisor se conecta, recibe todas las confirmaciones pendientes agrupadas por reader

---

## 4. Flujo Detallado Paso a Paso

### Escenario 1: Usuario abre un chat

#### Paso 1: Usuario selecciona un contacto

El usuario presiona sobre el nombre de un contacto en la lista de chats.

**Qué ocurre:**
- El `ChatController` detecta la selección del contacto
- Se llama a `MessageService.loadChatHistory(contact)`

**Componentes involucrados:**
- `ChatController`: Controlador de la vista de chat
- `MessageService`: Servicio que gestiona la lógica de mensajes

---

#### Paso 2: Carga del historial y detección de no leídos

El `MessageService` carga el historial y busca mensajes sin leer.

**Qué ocurre:**
- Se obtienen todos los mensajes del contacto desde `MessageRepository`
- Se consulta `getMessageIdsByStatus(contactUsername, DELIVERED, SENT)` para obtener IDs no leídos
- Si hay mensajes no leídos, se procede a marcarlos

**Pseudocódigo:**
```
FUNCION loadChatHistory(contacto):
    mensajes = messageRepository.findByContactUsername(contacto.username)
    
    idsNoLeidos = messageRepository.getMessageIdsByStatus(contacto.username, DELIVERED, SENT)
    
    SI idsNoLeidos NO está vacío:
        messageRepository.updateMultipleStatus(idsNoLeidos, READ)
        
        SI webSocketService.isConnected():
            messageSender.sendMarkAsRead(usuarioActual, contacto.username, idsNoLeidos)
            
        PARA CADA mensaje EN mensajes:
            SI mensaje.id EN idsNoLeidos:
                mensaje.setStatus(READ)
    
    actualizarUI(mensajes)
```

**Queries SQL ejecutadas:**
```sql
-- Obtener IDs de mensajes no leídos por estado
SELECT id FROM messages WHERE contact_username = ? AND status IN ('DELIVERED', 'SENT')

-- Actualizar estado a leído (batch)
UPDATE messages SET status = 'READ' WHERE id IN (?, ?, ?)
```

---

#### Paso 3: Envío de MarkMessagesAsReadRequest

El cliente envía la confirmación de lectura al servidor.

**Qué ocurre:**
- Se construye un `MarkMessagesAsReadRequest` protobuf
- Se envuelve en un `WsMessage`
- Se envía a través del WebSocket

**Estructura del mensaje:**
```protobuf
MarkMessagesAsReadRequest {
    sender: "usuarioActual",      // Usuario que lee los mensajes
    recipient: "contactoUsername", // Usuario que envió los mensajes originalmente
    message_ids: ["123", "124", "125"] // IDs de mensajes leídos
}
```

---

#### Paso 4: connection-service enruta la solicitud

El `MarkAsReadHandler` en connection-service procesa la solicitud.

**Qué ocurre:**
- El `ConnectionMessageDispatcher` recibe el mensaje WebSocket
- `MarkAsReadHandler` detecta el tipo de mensaje
- Se registra log con información de diagnóstico
- El mensaje se enruta al destinatario vía MessageRouterService

**Logs generados:**
```
=== MarkAsReadHandler === Sender: {sender}, Recipient: {recipient}, MessageIds: {ids}
MarkAsReadHandler: Mensaje serializado - {bytes} bytes
MarkAsReadHandler: Mensaje enrutado exitosamente
```

**Flujo de enrutamiento:**
```
connection-service (instancia A)
    ↓ RabbitMQ (si emisor offline: cola "message.offline")
connection-service (instancia B o misma instancia)
    ↓ WebSocket
Cliente emisor original
```

**Componentes involucrados:**
- `MarkAsReadHandler`: Handler para solicitudes de marcado como leído
- `MessageRouterService`: Servicio de enrutamiento de mensajes
- `RabbitMQProducerService`: Servicio para encolar mensajes

---

### Escenario 2: Llega mensaje mientras el chat está abierto

#### Paso 1: Mensaje entrante detectado

Un nuevo mensaje llega mientras el usuario tiene el chat abierto.

**Qué ocurre:**
- El `IncomingMessageProcessor` recibe el mensaje vía WebSocket
- `handleChatMessage()` detecta que el chat está abierto
- En lugar de marcar inmediatamente, se llama a `scheduleReadReceipt(senderId)`

**Pseudocódigo:**
```
FUNCION handleChatMessage(mensaje):
    mensajeGuardado = messageRepository.create(mensaje)
    
    contactoActual = getCurrentContact()
    
    SI contactoActual != null AND contactoActual.id == mensaje.contactId:
        Platform.runLater(() -> currentChatMessages.add(mensajeGuardado))
        scheduleReadReceipt(mensaje.senderId)
    SINO:
        updateNotification(mensaje.senderId)
```

---

#### Paso 2: Programación con Debounce

Se inicia el timer de debounce para evitar efecto rebote.

**Qué ocurre:**
- Se verifica si existe un timer previo para el contacto
- Si existe, se cancela (reset del debounce)
- Se crea un nuevo timer con delay configurable (`READ_RECEIPT_DEBOUNCE_MS`)
- El timer se almacena en `readReceiptTimers` map

**Pseudocódigo:**
```
FUNCION scheduleReadReceipt(senderId):
    timerExistente = readReceiptTimers.get(senderId)
    
    SI timerExistente != null AND !timerExistente.isCancelled():
        timerExistente.cancel()
    
    nuevoTimer = scheduler.schedule(() -> {
        executeReadReceipt(senderId)
    }, READ_RECEIPT_DEBOUNCE_MS, MILLISECONDS)
    
    readReceiptTimers.put(senderId, nuevoTimer)
```

**Ventajas del debounce:**
- Si llegan 5 mensajes en 1 segundo, solo se envía 1 confirmación
- Reduce la carga en el servidor
- Evita múltiples requests innecesarios
- Captura todos los mensajes que lleguen durante la ventana de debounce

---

#### Paso 3: Ejecución de la confirmación

Cuando el timer expira, se ejecuta la confirmación.

**Qué ocurre:**
- Se obtienen todos los IDs de mensajes no leídos del contacto
- Se actualiza el estado a READ en la base de datos local
- Se envía la confirmación al servidor
- Se actualiza la UI para mostrar los mensajes como leídos

**Pseudocódigo:**
```
FUNCION executeReadReceipt(senderId):
    idsNoLeidos = messageRepository.getMessageIdsByStatus(senderId, DELIVERED, SENT)
    
    SI idsNoLeidos está vacío:
        readReceiptTimers.remove(senderId)
        RETORNAR
    
    messageRepository.updateMultipleStatus(idsNoLeidos, READ)
    
    messageSender.sendMarkAsRead(usuarioActual, senderId, idsNoLeidos)
    
    Platform.runLater(() -> {
        PARA CADA mensaje EN currentChatMessages:
            SI mensaje.id EN idsNoLeidos:
                mensaje.setRead(true)
                mensaje.setStatus(READ)
    })
    
    readReceiptTimers.remove(senderId)
```

---

### Escenario 3: Emisor original offline

#### Paso 1: Receptor envía confirmación de lectura

El receptor marca los mensajes como leídos y envía la solicitud.

**Qué ocurre:**
- El cliente receptor envía `MarkMessagesAsReadRequest` a connection-service
- El mensaje contiene el `sender` (lector) y `recipient` (emisor original)

**Estructura del mensaje:**
```protobuf
MarkMessagesAsReadRequest {
    sender: "usuarioLector",      // Usuario que leyó los mensajes
    recipient: "emisorOriginal",  // Usuario que envió los mensajes
    message_ids: ["123", "124"]   // IDs de mensajes leídos
}
```

---

#### Paso 2: connection-service detecta emisor offline

El `MarkAsReadHandler` verifica el estado del emisor original.

**Qué ocurre:**
- Se consulta Redis para obtener la instancia de conexión del emisor
- Si no hay instancia registrada, el emisor está offline
- Se registra log de diagnóstico
- El mensaje se envía a la cola `message.offline` de RabbitMQ

**Logs generados:**
```
=== MarkAsReadHandler === Sender: {sender}, Recipient: {recipient}, MessageIds: {ids}
=== ENVIANDO A COLA OFFLINE === Destinatario: {recipient}, Sender: {sender}
```

**Flujo de decisión:**
```
MarkAsReadHandler.handle()
    │
    ├─ Emisor ONLINE → WebSocket directo al emisor
    │
    └─ Emisor OFFLINE → RabbitMQ "message.offline"
                      → Procesado por chat-service
```

**Componentes involucrados:**
- `MarkAsReadHandler`: Handler para solicitudes de marcado como leído
- `MessageRouterService`: Consulta Redis y determina ruta del mensaje
- `RabbitMQProducerService`: Encola mensaje en cola offline

---

#### Paso 3: chat-service procesa mensaje offline

El `OfflineMessageConsumer` recibe el mensaje de la cola RabbitMQ.

**Qué ocurre:**
- Se parsea el `WsMessage` protobuf desde el evento
- Se invoca `OfflineMessageDispatcher.dispatch()`
- Se busca un handler que soporte `MARK_MESSAGES_AS_READ_REQUEST`
- `OfflineMarkAsReadHandler` es seleccionado

**Flujo de dispatcher:**
```
OfflineMessageConsumer.handleOfflineMessage()
    │
    ▼
OfflineMessageDispatcher.dispatch()
    │
    │ PARA CADA handler EN handlers:
    │   SI handler.supports(message):
    │     handler.handleOffline(message, recipient)
    │     RETORNAR
    │
    ▼
OfflineMarkAsReadHandler.handleOffline()
```

---

#### Paso 4: OfflineMarkAsReadHandler persiste confirmación pendiente

El handler guarda cada mensaje como pendiente en la base de datos.

**Qué ocurre:**
- Se extrae `MarkMessagesAsReadRequest` del `WsMessage`
- Se obtienen los campos:
  - `sender`: usuario que leyó los mensajes
  - `recipient`: emisor original a notificar
  - `message_ids`: lista de IDs leídos
- Para cada ID se crea un `PendingReadReceipt` y se persiste

**Pseudocódigo:**
```
FUNCION handleOffline(message, recipient):
    request = message.getMarkMessagesAsReadRequest()
    
    reader = request.getSender()
    receiptRecipient = request.getRecipient()
    
    PARA CADA messageId EN request.getMessageIdsList():
        pendingReceipt = new PendingReadReceipt()
        pendingReceipt.setMessageId(messageId)
        pendingReceipt.setReceiptRecipient(receiptRecipient)
        pendingReceipt.setReader(reader)
        
        pendingReadReceiptRepository.save(pendingReceipt)
        
        LOG("Recibo de lectura pendiente guardado - mensaje: {}, leido por: {}, notificar a: {}")
```

**Modelo de datos:**
```sql
CREATE TABLE pending_read_receipts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    message_id VARCHAR(255),     -- ID del mensaje que fue leído
    receipt_recipient VARCHAR(255), -- Usuario a notificar (emisor original)
    reader VARCHAR(255)          -- Usuario que leyó el mensaje
);
```

---

#### Paso 5: Emisor se conecta y recibe confirmaciones pendientes

Cuando el emisor original se conecta al sistema:

**Qué ocurre:**
- `ConnectionWebSocketHandler.afterConnectionEstablished()` en connection-service
- Se llama a `PendingMessagesService.getPendingMessages(username)`
- HTTP GET a chat-service: `/api/v1/messages/pending/{username}`
- `MessageService.getAllPendingMessages()` recupera TODOS los pendientes
- Se agrupan las confirmaciones por `reader` para crear múltiples `MessagesReadUpdate`
- Se envuelven en un `MessagesReadUpdateList`
- Se eliminan los pendientes de la base de datos después del envío

**Agrupación por reader (importante):**

El sistema ahora agrupa las confirmaciones de lectura por `reader` para manejar correctamente el caso donde un usuario tiene mensajes leídos por múltiples contactos:

```
Ejemplo:
- Usuario A tiene mensajes con contacto B y contacto C
- Ambos leen sus mensajes mientras A está offline
- Se guardan:
  - PendingReadReceipt(messageId="1", reader="B", recipient="A")
  - PendingReadReceipt(messageId="2", reader="C", recipient="A")

Al reconectarse A:
- Se agrupan por reader: {"B": ["1"], "C": ["2"]}
- Se crea MessagesReadUpdateList con 2 actualizaciones
```

**Estructura del mensaje enviado:**
```protobuf
WsMessage {
    messages_read_update_list: {
        updates: [
            {
                message_ids: ["123", "124"],
                reader_username: "contactoB"
            },
            {
                message_ids: ["125"],
                reader_username: "contactoC"
            }
        ]
    }
}
```

---

#### Paso 6: Cliente del emisor actualiza estado

El cliente del emisor procesa la confirmación de lectura.

**Qué ocurre:**
- `IncomingMessageProcessor.processMessagesReadUpdateList()` recibe la lista
- Se itera sobre cada `MessagesReadUpdate` en la lista
- Se llama a `processMessagesReadUpdate()` para cada actualización
- Se actualiza el estado a READ en la base de datos SQLite local
- Se actualiza la UI para mostrar el indicador de lectura

**Pseudocódigo:**
```
FUNCION processMessagesReadUpdateList(updateList):
    LOG("=== RECIBIDO MessagesReadUpdateList === {} actualizaciones", updateList.getUpdatesCount())
    
    PARA CADA update EN updateList.getUpdatesList():
        processMessagesReadUpdate(update)


FUNCION processMessagesReadUpdate(update):
    ids = update.getMessageIdsList()
    readerUsername = update.getReaderUsername()
    
    messageRepository.updateMultipleStatus(ids, READ)
    
    Platform.runLater(() -> {
        PARA CADA mensaje EN currentChatMessages:
            SI mensaje.id EN ids:
                mensaje.setRead(true)
                mensaje.setStatus(READ)
    })
```

---

## 5. Base de Datos Local del Cliente

### Tabla messages

```sql
CREATE TABLE messages (
    id INTEGER PRIMARY KEY,
    contact_username TEXT NOT NULL,
    sender_username TEXT NOT NULL,
    content TEXT NOT NULL,
    sender_id TEXT NOT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    status TEXT DEFAULT 'PENDING',
    type TEXT DEFAULT 'text',
    downloaded INTEGER DEFAULT 0,
    FOREIGN KEY (contact_username) REFERENCES contacts(id) ON DELETE CASCADE
)
```

### Métodos del MessageRepository

| Método | Descripción |
|--------|-------------|
| `updateStatus(Long id, MessageStatus status)` | Actualiza el estado de un mensaje individual |
| `updateMultipleStatus(List<Long> ids, MessageStatus status)` | Actualiza el estado de múltiples mensajes (batch) |
| `getMessageIdsByStatus(String contact, MessageStatus... statuses)` | Obtiene IDs de mensajes por estado(s) |

---

## 6. Formato de Mensajes Protobuf

### MarkMessagesAsReadRequest

```protobuf
message MarkMessagesAsReadRequest {
    string sender = 1;
    string recipient = 2;
    repeated string message_ids = 3;
}
```

### MessagesReadUpdate

```protobuf
message MessagesReadUpdate {
    repeated string message_ids = 1;
    string reader_username = 2;
}
```

### MessagesReadUpdateList

```protobuf
message MessagesReadUpdateList {
    repeated MessagesReadUpdate updates = 1;
}
```

### MessageDeliveredUpdate

```protobuf
message MessageDeliveredUpdate {
    repeated string message_ids = 1;
    string delivered_to_username = 2;
}
```

---

## 7. Estructura de Clases Principales

### websocket-client

| Clase | Responsabilidad |
|-------|-----------------|
| `IncomingMessageProcessor` | Procesa mensajes entrantes, maneja debounce y handlers para `MessagesReadUpdateList` |
| `MessageService` | Gestiona la lógica de carga de historial y marcado de mensajes |
| `MessageSender` | Construye y envía mensajes protobuf al servidor |
| `MessageRepository` | Acceso a BD SQLite con métodos `updateStatus`, `updateMultipleStatus`, `getMessageIdsByStatus` |

### connection-service

| Classe | Responsabilidad |
|--------|-----------------|
| `MarkAsReadHandler` | Procesa `MarkMessagesAsReadRequest`, genera logs de diagnóstico |
| `MessageRouterService` | Determina la ruta del mensaje según el estado de conexión |
| `RabbitMQProducerService` | Encola mensajes en RabbitMQ |

### chat-service

| Clase | Responsabilidad |
|-------|-----------------|
| `OfflineMarkAsReadHandler` | Procesa `MarkMessagesAsReadRequest` cuando el emisor está offline |
| `MessageService` | Recupera pendientes, agrupa por reader, crea `MessagesReadUpdateList` |
| `PendingReadReceiptRepository` | Persiste y recupera confirmaciones de lectura pendientes |
| `OfflineMessageConsumer` | Consumidor RabbitMQ de la cola `message.offline` |

---

## 8. Resumen

El flujo de confirmación de lectura garantiza que:

1. **Eficiencia**: El mecanismo de debounce evita múltiples requests
2. **Consistencia**: Todos los mensajes no leídos se envían en un solo request
3. **Experiencia de usuario**: Los mensajes se marcan como leídos localmente inmediatamente
4. **Notificación en tiempo real**: El emisor recibe confirmación inmediata (si está online)
5. **Soporte offline**: Las confirmaciones se persisten y entregan al reconectarse
6. **Múltiples readers**: El sistema agrupa confirmaciones por reader correctamente
7. **Persistencia de estados**: Los estados (PENDING, SENT, DELIVERED, READ) se persisten en BD local
8. **Logs de diagnóstico**: Logs detallados en puntos críticos para facilitar debugging
