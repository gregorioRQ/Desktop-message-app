# Contrato de Interfaz: Eliminación de mensajes y borrado de historial

## Mensajes

### **Datos enviados por el cliente**

#### **Eliminación de un mensaje (`DeleteMessageRequest`)**
- **Campo**: `message_id` (string)
  **Descripción**: Identificador del mensaje.  
  **Obligatorio**: Sí.
- **Campo**: `sender_username` (string)
  **Descripción**: Username del propietario del mensaje.  
  **Obligatorio**: Sí.

#### **Solicitud de borrado de historial (`ClearHistoryRequest`)
- **Campo**: `sender` (string)
  **Descripción**: El username del usuario.
- **Campo**: `recipient` (string)
  **Descripción**: El username del receptor.

---

### **Datos recibidos por el cliente**

#### **Respuesta a eliminación de mensaje (`DeleteMessageResponse`)**
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la operación de bloqueo fue exitosa.  
  **Valores posibles**: `true` o `false`.
- **Campo**: `message` (string)  
  **Descripción**: Mensaje descriptivo del resultado de la operación.
- **Campo**: `message_id` (string)
  **Descripción**: Identificador del mensaje.

#### **Notificación de eliminación de mensaje (`MessageDeletedNotification`)**
- **Campo**: `message_id` (string)
  **Descripción**: Identificador del mensaje eliminado.
- **Campo**: `deleted_by` (string)
  **Descripción**: Username del usuario que eliminó el mensaje.

---

## Protocolos y Medios

### **Medio de Comunicación**
- **Protocolo**: WebSocket  
- **Formato de Mensajes**: Protobuf (`WsMessage`)

### **Estructura del Mensaje WebSocket**
Todos los mensajes enviados y recibidos están encapsulados en un mensaje de tipo `WsMessage`.

---

## ***Flujos de Comunicación***

### Flujo 1: Eliminación de mensaje "para todos" (Destinatario Online)

```
┌──────────┐     ┌─────────────────┐     ┌─────────┐     ┌────────────────┐
│ Cliente  │────►│connection-      │────►│RabbitMQ │────►│ chat-service   │
│ (A)      │     │ service         │     │ message.│     │ DeleteMessage  │
│ elimina  │     │ DeleteMessage   │     │ delete  │     │ Consumer       │
│ mensaje  │     │ Handler         │     │         │     │                │
└──────────┘     └─────────────────┘     └─────────┘     └───────┬────────┘
                                                                  │
                                                                  ▼
                                              ┌─────────────────────────────────┐
                                              │ 1. Eliminar mensaje de BD       │
                                              │ 2. Guardar PendingDeletion     │
                                              │ 3. Enviar a RabbitMQ          │
                                              └─────────────┬─────────────────┘
                                                            │
                          ┌─────────────────────────────────┼─────────────────┐
                          │                                 │                 │
                          ▼                                 ▼                 ▼
              ┌─────────────────────┐          ┌─────────────────────┐   ┌────────────────────┐
              │ RabbitMQ            │          │ RabbitMQ             │   │ PendingDeletion   │
              │ message.delivery    │          │ message.delivery    │   │ (guardar en BD)   │
              │ tipo: MESSAGE_DELETED│         │ tipo: MESSAGE_DELETED│   │                    │
              └─────────┬───────────┘          └──────────┬──────────┘   └────────────────────┘
                        │                                 │
                        ▼                                 ▼
              ┌─────────────────────────────────────────────────────────┐
              │              connection-service                          │
              │  DeliveryStatusConsumer.handleDeliveryStatus()          │
              │    - Detecta tipo "MESSAGE_DELETED"                   │
              │    - Reenvía al cliente via WebSocket                 │
              └─────────────────────────┬───────────────────────────────┘
                                        │
                                        ▼
                              ┌─────────────────────┐
                              │  Cliente (B)        │
                              │  WebSocket          │
                              │  processMessage    │
                              │  DeletedNotification│
                              └─────────────────────┘
```

**Pasos:**
1. Cliente A elimina mensaje → envía `DeleteMessageRequest` por WebSocket
2. `ConnectionWebSocketHandler` recibe el mensaje
3. `ConnectionMessageDispatcher` lo rutea a `DeleteMessageHandler`
4. `DeleteMessageHandler` llama a `MessageRouterService.routeDeletionRequest()`
5. `RabbitMQProducerService` envía a cola `message.delete`
6. `DeleteMessageConsumer` en chat-service procesa la eliminación:
   - Elimina el mensaje de la base de datos
   - Guarda `PendingDeletion` en la BD
   - Envía notificación via RabbitMQ (`message.delivery` tipo `MESSAGE_DELETED`)
7. `DeliveryStatusConsumer` en connection-service recibe la notificación
8. Reenvía `MessageDeletedNotification` al cliente B via WebSocket
9. Cliente B procesa la notificación y elimina el mensaje localmente

### Flujo 2: Eliminación de mensaje "para todos" (Destinatario Offline)

```
┌──────────┐     ┌─────────────────┐     ┌─────────┐     ┌────────────────┐
│ Cliente  │────►│connection-      │────►│RabbitMQ │────►│ chat-service   │
│ (A)      │     │ service         │     │ message.│     │ DeleteMessage  │
│ elimina  │     │ DeleteMessage   │     │ delete  │     │ Consumer       │
│ mensaje  │     │ Handler         │     │         │     │                │
└──────────┘     └─────────────────┘     └─────────┘     └───────┬────────┘
                                                                  │
                                                                  ▼
                                              ┌─────────────────────────────────┐
                                              │ 1. Eliminar mensaje de BD       │
                                              │ 2. Guardar PendingDeletion     │
                                              │ (destinatario offline)        │
                                              └─────────────────────────────────┘
```

**Pasos:**
1. Cliente A elimina mensaje → envía `DeleteMessageRequest` por WebSocket
2. El flujo en connection-service y chat-service es igual que en Flujo 1
3. Como el destinatario está offline, chat-service guarda `PendingDeletion` en BD
4. Cuando el destinatario se conecte, recibirá las eliminaciones pendientes

### Flujo 3: Recuperación de eliminaciones pendientes al conectar

```
┌──────────┐     ┌─────────────────┐     ┌────────────────────────────────┐
│ Cliente  │────►│connection-      │────►│ chat-service                   │
│ (B)      │     │ service         │     │ GET /api/v1/messages/pending/ │
│ conecta  │     │ PendingMessages │     │ {username}                     │
└──────────┘     │ Handler         │     └──────────────┬─────────────────┘
                └──────────────────┘                        │
                               ┌─────────────────────────────┼─────────────────┐
                               │                             │                 │
                               ▼                             ▼                 │
               ┌─────────────────────┐          ┌─────────────────────┐         │
               │ WsMessage          │          │ TODOS los pending  │         │
               │ (Base64)           │          │ - Mensajes         │         │
               │ - UnreadMessages   │          │ - Eliminaciones    │         │
               │ - PendingDeletions│          │ - Bloqueos         │         │
               │ - BlockedUsers     │          │ - Desbloqueos      │         │
               │ - UnblockedUsers   │          │ - ClearHistory    │         │
               │ - ClearHistory    │          │ - ReadReceipts    │         │
               │ - ReadReceipts    │          │ - ContactIdentity │         │
               │ - ContactIdentity │          │                   │         │
               └─────────────────────┘          └─────────────────────┘         │
                               │                                                 │
                               └─────────────────────────┬───────────────────┘
                                                         │
                                                         ▼
                                           ┌─────────────────────┐
                                           │  Cliente (B)        │
                                           │  Recibe WsMessage  │
                                           │  con todos los     │
                                           │  pendientes        │
                                           └─────────────────────┘
```

**Pasos:**
1. Cliente B se conecta a connection-service
2. `PendingMessagesHandler.sendPendingMessages()` obtiene TODOS los mensajes pendientes
3. El endpoint `/api/v1/messages/pending/{username}` devuelve un solo `WsMessage` con:
   - `UnreadMessagesList` - mensajes de chat no leídos
   - `PendingClearHistoryList` - IDs de mensajes eliminados
   - `BlockedUsersList` - usuarios bloqueados
   - `UnblockedUsersList` - usuarios desbloqueados
   - `PendingClearHistoryList` - limpiezas de historial
   - `MessagesReadUpdate` - confirmaciones de lectura
   - `ContactIdentity` - identidades de contacto
4. connection-service envía el `WsMessage` completo al cliente B via WebSocket
5. Cliente B procesa todos los pendientes y actualiza su estado local

---

## Flujo 4: Eliminación de historial desde el Cliente (UI)

### Descripción
Cuando el usuario presiona el botón "Vaciar Chat" en la interfaz, puede elegir entre:
- **Solo para mí**: Elimina los mensajes de la base de datos local únicamente
- **Para todos**: Envía solicitud al servidor para eliminar historial en ambos lados + elimina en DB local

### Componentes del Cliente

| Clase | Responsabilidad |
|-------|-----------------|
| `ChatController` | Botón UI que dispara `handleClearChat()` |
| `MessageActionHelper` | Maneja la acción y muestra el diálogo |
| `ChatDialogs` | Muestra diálogo de confirmación con opciones |
| `MessageService` | Coordina eliminación local y envío al servidor |
| `MessageSender` | Construye y envía el mensaje proto |
| `MessageRepository` | Elimina mensajes de la DB local SQLite |

### Estructura de la Base de Datos Local

```sql
CREATE TABLE IF NOT EXISTS messages (
    id INTEGER PRIMARY KEY,
    contact_username TEXT NOT NULL,      -- Contacto del chat
    sender_username TEXT NOT NULL,      -- Username de quien envió el mensaje
    content TEXT NOT NULL,              -- Contenido del mensaje
    sender_id TEXT NOT NULL,            -- ID del dispositivo/remitente
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_read INTEGER DEFAULT 0,
    FOREIGN KEY (contact_username) REFERENCES contacts(id) ON DELETE CASCADE
)
```

### Proto Messages (Cliente)

**ClearHistoryRequest** (messages.proto):
```protobuf
message ClearHistoryRequest {
  string sender = 1;      // Usuario que solicita la eliminación
  string recipient = 2;    // Contacto con quien eliminar historial
}
```

**WsMessage** (envuelve el request):
```protobuf
// En WsMessage.PayloadCase:
ClearHistoryRequest clear_history_request = 9;
```

### Diagrama de Flujo

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CLIENTE (JavaFX)                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  1. Usuario presiona botón "Vaciar Chat"                          │
│     └─> ChatController.clearChatButton.onAction                    │
│                                                                     │
│  2. MessageActionHelper.handleClearChat()                          │
│     └─> ChatDialogs.showClearChatDialog()                          │
│                                                                     │
│  3. Diálogo muestra opciones:                                      │
│     ┌─────────────────┐  ┌─────────────────┐  ┌───────────────┐  │
│     │ "Solo para mí"  │  │   "Para todos"  │  │  "Cancelar"   │  │
│     └────────┬────────┘  └────────┬────────┘  └───────┬───────┘  │
│              │                     │                     │          │
│              ▼                     ▼                     │          │
│     ┌────────────────┐    ┌──────────────────────────┴────────┐  │
│     │ deleteForEveryone = false │  deleteForEveryone = true     │  │
│     └────────┬────────┘    ┌──────────────┬────────────────────┘  │
│              │              │              │                        │
│              ▼              ▼              ▼                        │
│     ┌─────────────────────────────────────────────────────────────┐│
│     │         MessageService.clearChatHistory()                   ││
│     │  ┌─────────────────────────────────────────────────────┐   ││
│     │  │ if (deleteForEveryone && webSocket.is   ││
│     │ Connected()) │ │   messageSender.sendClearHistory(sender, recipient)│   ││
│     │  │      └─> WsMessage.setClearHistoryRequest()         │   ││
│     │  └─────────────────────────────────────────────────────┘   ││
│     │  ┌─────────────────────────────────────────────────────┐   ││
│     │  │ messageRepository.deleteByContactUsername()         │   ││
│     │  │      └─> DELETE FROM messages WHERE contact_username │   ││
│     │  └─────────────────────────────────────────────────────┘   ││
│     │  ┌─────────────────────────────────────────────────────┐   ││
│     │  │ Platform.runLater(() -> messages.clear())          │   ││
│     │  │      └─> Limpia UI si es el chat actual            │   ││
│     │  └─────────────────────────────────────────────────────┘   ││
│     └─────────────────────────────────────────────────────────────┘│
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    SERVER (WebSocket)                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  4. WsMessage con ClearHistoryRequest se envía por WebSocket      │
│                                                                     │
│  5. connection-service recibe y rutea a ClearHistoryHandler      │
│                                                                     │
│  6. chat-service procesa y elimina historial de ambos usuarios   │
│                                                                     │
│  7. Envía ClearHistoryResponse al cliente                        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Código Relevante (Cliente)

**MessageService.clearChatHistory():**
```java
public void clearChatHistory(Contact contact, boolean deleteForEveryone) {
    // Si es "para todos", enviar petición al servidor
    if (deleteForEveryone && webSocketService.isConnected()) {
        messageSender.sendClearHistory(currentUsername, contact.getContactUsername());
    }

    // Eliminar de la base de datos local (siempre se hace)
    messageRepository.deleteByContactUsername(contact.getContactUsername());
    
    // Limpiar la UI si estamos viendo ese chat
    if (currentContact != null && currentContact.getId() == contact.getId()) {
        Platform.runLater(() -> currentChatMessages.clear());
    }
}
```

**MessageSender.sendClearHistory():**
```java
public void sendClearHistory(String sender, String recipient) {
    MessagesProto.ClearHistoryRequest request = MessagesProto.ClearHistoryRequest.newBuilder()
        .setSender(sender)
        .setRecipient(recipient)
        .build();
    sendMessage(WsMessage.newBuilder().setClearHistoryRequest(request).build());
}
```

**MessageRepository.deleteByContactUsername():**
```java
public void deleteByContactUsername(String contactUsername) throws SQLException {
    String sql = "DELETE FROM messages WHERE contact_username = ?";
    // Ejecuta delete y retorna cantidad de mensajes eliminados
}
```

### Procesamiento de Respuesta del Servidor

El servidor responde con `ClearHistoryResponse`, которое se procesa en:
- `IncomingMessageProcessor.processClearHistoryRequest()` (línea 50, 170 en MessageService)

---

## Flujo 5: Recepción de historial pendiente al conectar (Offline)

### Descripción
Cuando un usuario se conecta después de haber estado offline, puede haber solicitudes
de eliminación de historial que otros usuarios hicieron mientras estaba desconectado.
El servidor envía estas solicitudes en un `PendingClearHistoryList`.

### Proto Messages

**PendingClearHistory** (messages.proto):
```protobuf
message PendingClearHistory {
  string sender = 1;       // Username del usuario que solicitó la limpieza
  string recipient = 2;    // Username del usuario que debe aplicar la limpieza
}

message PendingClearHistoryList {
  repeated PendingClearHistory clear_histories = 1;
}
```

**WsMessage** (envuelve la lista):
```protobuf
// En WsMessage.PayloadCase:
PendingClearHistoryList pending_clear_history_list = 22;
```

### Procesamiento en el Cliente

El cliente recibe `PendingClearHistoryList` y para cada entrada:
1. Verifica que `recipient` coincida con el `currentUsername` del usuario actual
2. Busca el contacto local por `sender` (quien solicitó la limpieza)
3. Elimina todos los mensajes con ese contacto de la DB local
4. Si el usuario está viendo ese chat, limpia la UI

**IMPORTANTE - Bug corregido:**
Anteriormente el código comparaba `recipient` con `currentUserId` (ej: "user-123"),
pero `PendingClearHistory.recipient` contiene el **username** (ej: "juan").
La corrección compara con `currentUsername` en lugar de `currentUserId`.

### Código Relevante

**IncomingMessageProcessor.processPendingClearHistoryList():**
```java
private void processPendingClearHistoryList(PendingClearHistoryList list) {
    String currentUsername = context.getCurrentUsernameSupplier().get();
    
    for (PendingClearHistory clearHistory : list.getClearHistoriesList()) {
        String senderUsername = clearHistory.getSender();
        String recipientUsername = clearHistory.getRecipient();
        
        // Verificar que esta solicitud es para este usuario
        // IMPORTANTE: Comparar recipientUsername (username) con currentUsername
        if (!recipientUsername.equals(currentUsername)) {
            continue; // Ignorar solicitudes dirigidas a otros usuarios
        }
        
        // Buscar el contacto por username
        Optional<Contact> contactOpt = contactService.findContactByUsername(currentUserId, senderUsername);
        
        if (contactOpt.isPresent()) {
            // Eliminar mensajes de la BD local
            messageRepository.deleteByContactUsername(contactUsername);
            
            // Limpiar UI si es el chat actual
            if (currentContact != null && currentContact.getContactUsername().equals(contactUsername)) {
                currentChatMessages.clear();
            }
        }
    }
}
```

### Diferencia entre Flujos

| Método | Cuándo se invoca | Fuente |
|--------|------------------|--------|
| `processClearHistoryRequest` | Usuario elimina historial MIENTRAS estás online | WebSocket en tiempo real |
| `processPendingClearHistoryList` | Te conectas después de estar offline | Pending messages del servidor |

---

## Procesamiento en Chat-Service

### OfflineClearHistoryHandler

Cuando el destinatario está offline, la solicitud de eliminación de historial llega a chat-service
a través de la cola `message.offline`. El handler `OfflineClearHistoryHandler` procesa esta solicitud:

**Ubicación:** `chat-service/.../handler/OfflineClearHistoryHandler.java`

**Acciones realizadas:**
1. Elimina los mensajes de la BD del servidor entre ambos usuarios (en ambas direcciones)
2. Guarda una notificación pendiente (`PendingClearHistory`) para que el destinatario elimine su copia local

**Diferencia con DeleteMessageHandler:**
- `DeleteMessageHandler`: Elimina UN mensaje específico
- `OfflineClearHistoryHandler`: Elimina TODOS los mensajes entre dos usuarios

**Código relevante:**
```java
@Override
public void handleOffline(MessagesProto.WsMessage message, String recipient) throws Exception {
    MessagesProto.ClearHistoryRequest request = message.getClearHistoryRequest();
    String sender = request.getSender();
    
    // 1. Eliminar mensajes de la BD entre ambos usuarios
    messageRepository.deleteAllByFromUserIdAndToUserId(sender, recipient);
    messageRepository.deleteAllByFromUserIdAndToUserId(recipient, sender);
    
    // 2. Guardar notificación pendiente para el cliente
    PendingClearHistory pendingClearHistory = new PendingClearHistory();
    pendingClearHistory.setSender(sender);
    pendingClearHistory.setRecipient(recipient);
    pendingClearHistoryRepository.save(pendingClearHistory);
}
```

### Flujo Completo (Servidor)

```
Usuario A elimina historial con B (presiona "Para todos")
    │
    ▼
connection-service recibe ClearHistoryRequest
    │
    ├─── B online ──► Envía directo a B por WebSocket
    │                  B elimina historial local
    │
    └─── B offline ──► Envía a cola message.offline
                          │
                          ▼
                    chat-service
                    OfflineClearHistoryHandler
                          │
    ┌──────────────────────┼──────────────────────┐
    ▼                      ▼                      ▼
Elimina mensajes       Guarda PendingClear    Envía PendingClearHistoryList
en BD (A→B y B→A)     History (cuando B      cuando B se conecte
                      se conecte)
```

---

## Procesamiento en el Cliente (WebSocket)

### IncomingMessageProcessor

El cliente (JavaFX) recibe los mensajes pendientes a través de WebSocket y los procesa
usando `IncomingMessageProcessor`. Este componente tiene handlers específicos para cada
tipo de mensaje recibido.

**Ubicación:** `websocket-client/src/main/java/com/pola/service/IncomingMessageProcessor.java`

### Handlers para Eliminación de Historial

| Handler | Tipo de Mensaje | Cuándo se Invoca |
|---------|-----------------|------------------|
| `processClearHistoryRequest` | `CLEAR_HISTORY_REQUEST` | El otro usuario elimina historial contigo MIENTRAS estás online |
| `processPendingClearHistoryList` | `PENDING_CLEAR_HISTORY_LIST` | Te conectas después de haber estado offline |
| `processMessageDeletedNotification` | `MESSAGE_DELETE_NOTIFICATION` | Otro usuario elimina un mensaje específico "para todos" |

### Código Relevante - processClearHistoryRequest

```java
private void processClearHistoryRequest(MessagesProto.ClearHistoryRequest request) {
    String senderUsername = request.getSender();
    log.info("Recibida solicitud de limpieza de historial en tiempo real - Solicitante: {}", senderUsername);
    
    try {
        // Eliminar todos los mensajes con el usuario que solicitó la limpieza
        context.getMessageRepository().deleteByContactUsername(senderUsername);
        
        // Si estamos viendo el chat de este contacto, limpiar la UI
        Contact current = context.getCurrentContactSupplier().get();
        if (current != null && current.getContactUsername().equals(senderUsername)) {
            Platform.runLater(() -> context.getCurrentChatMessages().clear());
        }
    } catch (SQLException e) {
        log.error("Error al procesar solicitud de limpieza de historial: {}", e.getMessage());
    }
}
```

### Código Relevante - processPendingClearHistoryList

```java
private void processPendingClearHistoryList(PendingClearHistoryList list) {
    String currentUsername = context.getCurrentUsernameSupplier().get();
    
    for (PendingClearHistory clearHistory : list.getClearHistoriesList()) {
        String senderUsername = clearHistory.getSender();
        String recipientUsername = clearHistory.getRecipient();
        
        // Verificar que esta solicitud es para este usuario
        if (!recipientUsername.equals(currentUsername)) {
            continue; // Ignorar solicitudes dirigidas a otros usuarios
        }
        
        // Buscar el contacto por username
        Optional<Contact> contactOpt = contactService.findContactByUsername(currentUserId, senderUsername);
        
        if (contactOpt.isPresent()) {
            // Eliminar mensajes de la BD local
            messageRepository.deleteByContactUsername(contactUsername);
            
            // Limpiar UI si es el chat actual
            if (currentContact != null && currentContact.getContactUsername().equals(contactUsername)) {
                currentChatMessages.clear();
            }
        }
    }
}
```

### Bug Corregido

**Problema original:**
El código comparaba `recipientUsername` con `currentUserId` (ej: "user-123"),
pero `PendingClearHistory.recipient` contiene el **username** (ej: "juan"),
no el userId.

**Solución:**
Ahora compara `recipientUsername` con `currentUsername` (obtenido de `context.getCurrentUsernameSupplier().get()`).

### Flujo Completo del Cliente

```
Cliente conecta a connection-service
        │
        ▼
connection-service → GET /api/v1/messages/pending/{username} → chat-service
        │
        ▼
chat-service retorna WsMessage con PendingClearHistoryList
        │
        ▼
Cliente recibe WsMessage por WebSocket
        │
        ▼
IncomingMessageProcessor.process(message)
        │
        ▼
processPendingClearHistoryList() o processClearHistoryRequest()
        │
        ▼
messageRepository.deleteByContactUsername(contactUsername)
        │
        ▼
Platform.runLater(() -> currentChatMessages.clear()) // Si es chat activo
```
