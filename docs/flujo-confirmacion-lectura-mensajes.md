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
| `MessagesReadUpdate` | Servidor → Cliente (emisor) | Notifica al emisor que sus mensajes fueron leídos |

### Canales de Comunicación

- **WebSocket**: Comunicación bidireccional para enviar confirmaciones y recibir actualizaciones en tiempo real
- **RabbitMQ**: Cola para enrutar confirmaciones entre instancias del connection-service

---

## 2. Escenarios del Flujo

El sistema maneja tres escenarios principales para la confirmación de lectura:

### Escenario 1: Usuario abre un chat

Cuando un usuario presiona sobre un contacto para abrir el chat, el sistema:
1. Carga el historial de mensajes del contacto
2. Identifica los mensajes no leídos en la base de datos local
3. Los marca como leídos localmente
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
3. El mensaje se almacena como pendiente en chat-service
4. Cuando el emisor se conecta, recibe todas las confirmaciones pendientes

---

## 3. Flujo Detallado Paso a Paso

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
- Se consulta `getUnreadMessageIds(contactUsername)` para obtener IDs no leídos
- Si hay mensajes no leídos, se procede a marcarlos

**Pseudocódigo:**
```
FUNCION loadChatHistory(contacto):
    mensajes = messageRepository.findByContactUsername(contacto.username)
    
    idsNoLeidos = messageRepository.getUnreadMessageIds(contacto.username)
    
    SI idsNoLeidos NO está vacío:
        messageRepository.markMultipleAsRead(idsNoLeidos)
        
        SI webSocketService.isConnected():
            messageSender.sendMarkAsRead(usuarioActual, contacto.username, idsNoLeidos)
    
    actualizarUI(mensajes)
```

**Queries SQL ejecutadas:**
```sql
-- Obtener IDs de mensajes no leídos
SELECT id FROM messages WHERE contact_username = ? AND is_read = 0

-- Marcar como leídos (batch)
UPDATE messages SET is_read = 1 WHERE id IN (?, ?, ?)
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
    message_ids: ["123", "124", "125"]  // IDs de mensajes leídos
}
```

---

#### Paso 4: connection-service enruta la solicitud

El `MarkAsReadHandler` en connection-service procesa la solicitud.

**Qué ocurre:**
- El `ConnectionMessageDispatcher` recibe el mensaje WebSocket
- `MarkAsReadHandler` detecta el tipo de mensaje
- El mensaje se enruta al destinatario vía RabbitMQ

**Flujo de enrutamiento:**
```
connection-service (instancia A)
    ↓ RabbitMQ
connection-service (instancia B o misma instancia)
    ↓ WebSocket
Cliente emisor original
```

**Componentes involucrados:**
- `MarkAsReadHandler`: Handler para solicitudes de marcado como leído
- `MessageRouterService`: Servicio de enrutamiento de mensajes

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
        scheduleReadReceipt(mensaje.senderId)  // ← Debounce
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

**Parámetro de configuración:**
```java
// HttpConfig.java
public static final long READ_RECEIPT_DEBOUNCE_MS = 3_000; // 3 segundos
```

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
- Se marcan como leídos en la base de datos local
- Se envía la confirmación al servidor
- Se actualiza la UI para mostrar los mensajes como leídos

**Pseudocódigo:**
```
FUNCION executeReadReceipt(senderId):
    idsNoLeidos = messageRepository.getUnreadMessageIds(senderId)
    
    SI idsNoLeidos está vacío:
        readReceiptTimers.remove(senderId)
        RETORNAR
    
    messageRepository.markMultipleAsRead(idsNoLeidos)
    
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
    sender: "usuarioLector",       // Usuario que leyó los mensajes
    recipient: "emisorOriginal",   // Usuario que envió los mensajes
    message_ids: ["123", "124"]    // IDs de mensajes leídos
}
```

---

#### Paso 2: connection-service detecta emisor offline

El `MarkAsReadHandler` verifica el estado del emisor original.

**Qué ocurre:**
- Se consulta Redis para obtener la instancia de conexión del emisor
- Si no hay instancia registrada, el emisor está offline
- El mensaje se envía a la cola `message.offline` de RabbitMQ

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
    │     SI handler.supports(message):
    │         handler.handleOffline(message, recipient)
    │         RETORNAR
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
    message_id VARCHAR(255),         -- ID del mensaje que fue leído
    receipt_recipient VARCHAR(255), -- Usuario a notificar (emisor original)
    reader VARCHAR(255)             -- Usuario que leyó el mensaje
);
```

**Componentes involucrados:**
- `OfflineMarkAsReadHandler`: Handler que procesa `MarkMessagesAsReadRequest` offline
- `PendingReadReceipt`: Entidad JPA para confirmaciones pendientes
- `PendingReadReceiptRepository`: Repositorio para persistir pendientes

---

#### Paso 5: Emisor se conecta y recibe confirmaciones pendientes

Cuando el emisor original se conecta al sistema:

**Qué ocurre:**
- `ConnectionWebSocketHandler.afterConnectionEstablished()` en connection-service
- Se llama a `PendingMessagesService.getPendingMessages(username)`
- HTTP GET a chat-service: `/api/v1/messages/pending/{username}`
- `MessageService.getAllPendingMessages()` recupera TODOS los pendientes
- Se incluyen las `MessagesReadUpdate` con los message_ids pendientes
- Se eliminan los pendientes de la base de datos después del envío

**Flujo de recuperación:**
```
connection-service
    │
    │ afterConnectionEstablished(username)
    ▼
PendingMessagesService.getPendingMessages(username)
    │
    ▼ HTTP GET
chat-service:8085/api/v1/messages/pending/{username}
    │
    ▼
MessageService.getAllPendingMessages(username)
    │
    ├─ getAndClearPendingReadReceipts(username)
    │     │
    │     ▼
    │   SELECT * FROM pending_read_receipts 
    │   WHERE receipt_recipient = ?
    │     │
    │     ▼
    │   DELETE FROM pending_read_receipts 
    │   WHERE receipt_recipient = ?
    │
    ▼
Construir WsMessage con MessagesReadUpdate
    │
    ▼
Enviar via WebSocket al cliente emisor
```

**Estructura del mensaje enviado:**
```protobuf
WsMessage {
    messages_read_update: {
        message_ids: ["123", "124", "125"],
        reader_username: "usuarioLector"
    }
}
```

---

#### Paso 6: Cliente del emisor actualiza estado

El cliente del emisor procesa la confirmación de lectura.

**Qué ocurre:**
- `IncomingMessageProcessor.processMessagesReadUpdate()` recibe la actualización
- Se marcan los mensajes como leídos en la base de datos SQLite local
- Se actualiza la UI para mostrar el check doble azul (READ)
- El emisor ve que sus mensajes fueron leídos

**Pseudocódigo:**
```
FUNCION processMessagesReadUpdate(update):
    ids = update.getMessageIdsList()
    readerUsername = update.getReaderUsername()
    
    messageRepository.markMultipleAsRead(ids)
    
    Platform.runLater(() -> {
        PARA CADA mensaje EN currentChatMessages:
            SI mensaje.id EN ids:
                mensaje.setRead(true)
                mensaje.setStatus(MessageStatus.READ)
    })
```

---

## 4. Recepción de Confirmación por el Emisor

### Paso 1: El emisor recibe MessagesReadUpdate

El cliente del emisor original recibe la confirmación.

**Qué ocurre:**
- `IncomingMessageProcessor` recibe el `MessagesReadUpdate`
- `processMessagesReadUpdate()` procesa la actualización
- Se actualiza la base de datos local y la UI

**Estructura del mensaje recibido:**
```protobuf
MessagesReadUpdate {
    message_ids: ["123", "124", "125"],
    reader_username: "usuarioQueLeyo"
}
```

**Pseudocódigo:**
```
FUNCION processMessagesReadUpdate(update):
    ids = update.getMessageIdsList()
    readerUsername = update.getReaderUsername()
    
    messageRepository.markMultipleAsRead(ids)
    
    Platform.runLater(() -> {
        PARA CADA mensaje EN currentChatMessages:
            SI mensaje.id EN ids:
                mensaje.setRead(true)
                mensaje.setStatus(MessageStatus.READ)
    })
```

---

## 5. Diagrama del Flujo

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ESCENARIO 1: USUARIO ABRE CHAT                                              │
└─────────────────────────────────────────────────────────────────────────────┘

[Usuario presiona contacto]
        │
        ▼
┌──────────────────────┐
│ MessageService       │
│ loadChatHistory()    │
└──────────────────────┘
        │
        ▼
┌──────────────────────┐
│ MessageRepository    │
│ getUnreadMessageIds()│
└──────────────────────┘
        │
        ▼
┌──────────────────────┐     ┌──────────────────────┐
│ Si hay mensajes      │────►│ markMultipleAsRead() │
│ no leídos            │     │ (DB local)           │
└──────────────────────┘     └──────────────────────┘
        │
        ▼
┌──────────────────────┐
│ MessageSender        │
│ sendMarkAsRead()     │
└──────────────────────┘
        │
        ▼ WebSocket
┌──────────────────────┐
│ connection-service   │
│ MarkAsReadHandler    │
└──────────────────────┘
        │
        ▼ RabbitMQ
┌──────────────────────┐
│ connection-service   │────► [Cliente emisor recibe]
│ (instancia emisor)   │      MessagesReadUpdate
└──────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│ ESCENARIO 2: LLEGA MENSAJE MIENTRAS CHAT ABIERTO                            │
└─────────────────────────────────────────────────────────────────────────────┘

[Llega mensaje del contacto]
        │
        ▼
┌──────────────────────┐
│ IncomingMessageProc  │
│ handleChatMessage()  │
└──────────────────────┘
        │
        │ ¿Chat abierto?
        ▼
┌──────────────────────┐
│ scheduleReadReceipt()│
│ (inicia debounce)    │
└──────────────────────┘
        │
        │ Timer 3 segundos
        │ (se resetea si llegan más mensajes)
        ▼
┌──────────────────────┐
│ executeReadReceipt() │
│ (timer expira)       │
└──────────────────────┘
        │
        ▼
┌──────────────────────┐
│ getUnreadMessageIds()│
│ + markMultipleAsRead │
│ + sendMarkAsRead()   │
└──────────────────────┘
        │
        ▼
┌──────────────────────┐
│ UI actualizada       │
│ mensajes = READ      │
└──────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│ DEBOUNCE TIMER DETAIL                                                       │
└─────────────────────────────────────────────────────────────────────────────┘

Mensaje 1 ──────► Timer iniciado (3s)
                       │
Mensaje 2 ──────► Timer reseteado (3s)
                       │
Mensaje 3 ──────► Timer reseteado (3s)
                       │
                       ▼
              Timer expira ──► executeReadReceipt()
                                   │
                                   ▼
                           Envía [ID1, ID2, ID3]
                           en un solo request


┌─────────────────────────────────────────────────────────────────────────────┐
│ ESCENARIO 3: EMISOR ORIGINAL OFFLINE                                         │
└─────────────────────────────────────────────────────────────────────────────┘

[Receptor lee mensajes]
        │
        ▼
┌──────────────────────┐
│ websocket-client     │
│ (receptor)           │
│ sendMarkAsRead()     │
│ (MarkMessagesAsRead  │
│  Request)            │
└──────────────────────┘
        │
        ▼ WebSocket
┌──────────────────────┐
│ connection-service   │
│ MarkAsReadHandler    │
│                      │
│ ¿Emisor online?      │
│         │            │
│         └── NO ──────┼──► RabbitMQ "message.offline"
└──────────────────────┘              │
                                      ▼
                            ┌──────────────────────┐
                            │ chat-service         │
                            │ OfflineMessageConsumer
                            │         ↓            │
                            │ OfflineMessageDispatcher
                            │         ↓            │
                            │ OfflineMarkAsReadHandler
                            └──────────────────────┘
                                      │
                                      ▼
                            ┌──────────────────────┐
                            │ pending_read_receipts│
                            │ (tabla en BD)        │
                            │                      │
                            │ id | messageId |     │
                            │    | reader    |     │
                            │    | recipient │     │
                            └──────────────────────┘

        ┌──────────────────────────────────────────┐
        │ Cuando el emisor se conecta:             │
        └──────────────────────────────────────────┘
                      │
                      ▼
              ┌──────────────────────┐
              │ connection-service   │
              │ afterConnection      │
              │ Established()        │
              └──────────────────────┘
                      │
                      ▼ HTTP GET
              ┌──────────────────────┐
              │ chat-service         │
              │ MessageService       │
              │ getAllPendingMessages│
              │         ↓            │
              │ getAndClearPending   │
              │ ReadReceipts()       │
              └──────────────────────┘
                      │
                      ▼
              ┌──────────────────────┐
              │ WsMessage con        │
              │ MessagesReadUpdate   │
              │ (message_ids, reader)│
              └──────────────────────┘
                      │
                      ▼ WebSocket
              ┌──────────────────────┐
              │ websocket-client     │
              │ (emisor original)    │
              │ processMessagesRead  │
              │ Update()             │
              │         ↓            │
              │ Marcar como READ     │
              │ Actualizar UI       │
              └──────────────────────┘
```

---

## 6. Estructura de Clases Principales

### websocket-client

| Clase | Responsabilidad |
|-------|-----------------|
| `IncomingMessageProcessor` | Procesa mensajes entrantes y maneja el debounce de confirmaciones |
| `MessageService` | Gestiona la lógica de carga de historial y marcado de mensajes |
| `MessageSender` | Construye y envía mensajes protobuf al servidor |
| `MessageRepository` | Acceso a base de datos SQLite local |
| `HttpConfig` | Configuración centralizada incluyendo `READ_RECEIPT_DEBOUNCE_MS` |

### connection-service

| Classe | Responsabilidad |
|--------|-----------------|
| `MarkAsReadHandler` | Procesa `MarkMessagesAsReadRequest` y los enruta al destinatario |
| `MessageRouterService` | Determina la ruta del mensaje según el estado de conexión |

### chat-service

| Clase | Responsabilidad |
|-------|-----------------|
| `OfflineMarkAsReadHandler` | Procesa `MarkMessagesAsReadRequest` cuando el emisor está offline, persiste en `pending_read_receipts` |
| `PendingReadReceiptRepository` | Persiste y recupera confirmaciones de lectura pendientes |
| `OfflineMessageConsumer` | Consumidor RabbitMQ que recibe mensajes de la cola `message.offline` |
| `OfflineMessageDispatcher` | Dispatcher que enruta mensajes offline al handler apropiado |
| `MessageService` | Recupera todos los pendientes (incluyendo confirmaciones de lectura) cuando un usuario se conecta |
| `PendingReadReceipt` | Entidad JPA que representa una confirmación de lectura pendiente |

---

## 7. Formato de Mensajes Protobuf

### MarkMessagesAsReadRequest

Enviado del cliente al servidor para solicitar marcado de mensajes como leídos:

```protobuf
message MarkMessagesAsReadRequest {
    string sender = 1;      // Usuario que lee los mensajes
    string recipient = 2;   // Usuario que envió los mensajes originalmente
    repeated string message_ids = 3;  // Lista de IDs de mensajes leídos
}
```

### MessagesReadUpdate

Enviado del servidor al cliente emisor para notificar que sus mensajes fueron leídos:

```protobuf
message MessagesReadUpdate {
    repeated string message_ids = 1;  // IDs de mensajes que fueron leídos
    string reader_username = 2;       // Usuario que leyó los mensajes
}
```

---

## 8. Configuración

### Parámetros Configurables

| Parámetro | Ubicación | Valor Default | Descripción |
|-----------|-----------|---------------|-------------|
| `READ_RECEIPT_DEBOUNCE_MS` | `HttpConfig.java` | 3000ms (3s) | Tiempo de debounce antes de enviar confirmación |

### Modificación del Delay

Para cambiar el tiempo de debounce, modificar en `websocket-client/src/main/java/com/pola/config/HttpConfig.java`:

```java
public static final long READ_RECEIPT_DEBOUNCE_MS = 5_000; // 5 segundos
```

---

## 9. Manejo de Errores

### SQLException en Base de Datos Local

Si ocurre un error al acceder a la base de datos SQLite:
- Se registra el error en el log
- El timer se limpia del mapa `readReceiptTimers`
- La UI no se actualiza (los mensajes permanecen como no leídos)

### Timer Cleanup

Los timers se limpian automáticamente:
- Al completar exitosamente la confirmación
- Al ocurrir una excepción
- Al cancelar por un nuevo mensaje del mismo contacto

---

## 10. Tests Unitarios

Los tests se encuentran en `IncomingMessageProcessorReadReceiptTest.java`:

### Tests de scheduleReadReceipt

| Test | Descripción |
|------|-------------|
| `testScheduleReadReceipt_NewTimer` | Verifica que se crea un nuevo timer |
| `testScheduleReadReceipt_ResetTimer` | Verifica que el timer se cancela y reinicia |
| `testScheduleReadReceipt_DifferentContacts` | Verifica timers independientes por contacto |

### Tests de executeReadReceipt

| Test | Descripción |
|------|-------------|
| `testExecuteReadReceipt_WithUnreadMessages` | Verifica obtención, marcado y envío |
| `testExecuteReadReceipt_ClearTimerAfterExecution` | Verifica limpieza del timer |
| `testExecuteReadReceipt_NoUnreadMessages` | Verifica que no envía si no hay mensajes |
| `testExecuteReadReceipt_SqlException` | Verifica manejo de errores de BD |

### Tests de OfflineMarkAsReadHandler (chat-service)

Los tests se encuentran en `OfflineMarkAsReadHandlerTest.java`:

| Test | Descripción |
|------|-------------|
| `testSupports_MarkMessagesAsReadRequest` | Verifica que supports() retorna true para `MarkMessagesAsReadRequest` |
| `testHandleOffline_SavesPendingReadReceipts` | Verifica que se guardan los pendientes correctamente |
| `testHandleOffline_MultipleMessageIds` | Verifica que se guarda un registro por cada messageId |
| `testHandleOffline_ExtractsCorrectFields` | Verifica que sender→reader y recipient→receiptRecipient |

### Tests de PendingReadReceiptRepository (chat-service)

Los tests se encuentran en `MessageServicePendingReadReceiptsTest.java`:

| Test | Descripción |
|------|-------------|
| `savePendingReadReceipts_HappyPath` | Verifica guardado exitoso de confirmaciones pendientes |
| `getAndClearPendingReadReceipts_HappyPath` | Verifica recuperación y eliminación de pendientes |
| `getAndClearPendingReadReceipts_NoPendingReceipts` | Verifica manejo cuando no hay pendientes |

---

## 11. Resumen

El flujo de confirmación de lectura garantiza que:

1. **Eficiencia**: El mecanismo de debounce evita múltiples requests cuando llegan mensajes en ráfaga
2. **Consistencia**: Todos los mensajes no leídos se envían en un solo request
3. **Experiencia de usuario**: Los mensajes se marcan como leídos localmente inmediatamente
4. **Notificación en tiempo real**: El emisor original recibe confirmación inmediata (si está online)
5. **Configurabilidad**: El delay de debounce es configurable sin cambios de código
6. **Multi-contacto**: Cada contacto tiene su propio timer independiente
7. **Soporte offline**: Cuando el emisor está offline, las confirmaciones se persisten y se entregan al reconectarse
