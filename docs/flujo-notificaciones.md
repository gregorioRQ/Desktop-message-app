# Flujo de Notificaciones

Este documento describe el flujo completo de notificaciones desde que un usuario envía un mensaje hasta que el receptor recibe la notificación push. El sistema utiliza una arquitectura basada en microservicios con RabbitMQ para la mensajería asíncrona y Server-Sent Events (SSE) para las notificaciones push en tiempo real.

---

## 1. Arquitectura General

### Servicios Involucrados

| Servicio | Puerto | Responsabilidad |
|----------|--------|-----------------|
| connection-service | 8083 | Maneja conexiones WebSocket de los clientes y enruta mensajes |
| chat-service | 8085 | Gestiona almacenamiento de mensajes y mensajes pendientes |
| notification-service | 8084 | Envía notificaciones push a los clientes mediante SSE |
| profile-service | 8088 | Gestión de usuarios y autenticación |

### Colas de RabbitMQ

El sistema utiliza un exchange directo llamado `message.exchange` con múltiples colas:

| Cola | Routing Key | Propósito | Productor | Consumidor |
|------|-------------|-----------|-----------|------------|
| `message.sent.{instanceId}` | `{instanceId}` | Mensajes para usuarios online en una instancia específica | connection-service | chat-service |
| `message.offline` | `offline` | Mensajes para usuarios offline | connection-service | chat-service |
| `message.notification` | `notification` | Eventos de notificación para nuevos mensajes | connection-service | notification-service |
| `message.delivery` | `delivery` | Estados de entrega (DELIVERED, READ) | chat-service | connection-service |

### Canales de Comunicación

- **WebSocket**: Comunicación bidireccional entre cliente y connection-service para mensajes en tiempo real
- **SSE (Server-Sent Events)**: Notificaciones push unidireccionales desde notification-service al cliente
- **REST API**: Recuperación de mensajes pendientes y operaciones de usuario

---

## 2. Flujo Detallado Paso a Paso

### Paso 1: Cliente envía mensaje

El cliente JavaFX envía un mensaje a través de WebSocket al connection-service.

**Qué ocurre:**
- El usuario escribe un mensaje y presiona enviar
- El cliente construye un mensaje protobuf `WsMessage` con un `ChatMessage` anidado
- El mensaje se envía a través de la conexión WebSocket establecida

**Datos del mensaje:**
```
ChatMessage:
  - messageId: ID único del mensaje
  - sender: username del remitente
  - recipient: username del destinatario
  - content: contenido del mensaje
  - timestamp: marca de tiempo del envío
```

---

### Paso 2: Llega a connection-service

El mensaje llega al connection-service a través del WebSocket.

**Qué ocurre:**
- El `ConnectionMessageDispatcher` recibe el mensaje WebSocket
- El dispatcher itera sobre los handlers registrados hasta encontrar uno que soporte el tipo de mensaje
- `ChatMessageHandler` detecta que es un `ChatMessage` y lo procesa

**Componentes involucrados:**
- `ConnectionMessageDispatcher`: Dispatcher que delega el mensaje al handler correspondiente
- `ChatMessageHandler`: Handler específico para mensajes de chat, implementa `ConnectionWsMessageHandler`

**Validaciones:**
- Verifica que la sesión WebSocket del usuario está activa
- Extrae el username del remitente de la sesión
- Valida que el mensaje contiene los campos requeridos

---

### Paso 3: MessageRouterService consulta Redis

El `MessageRouterService` determina la ruta óptima del mensaje consultando el estado de conexión del destinatario en Redis.

**Qué ocurre:**
- Se consulta Redis para obtener el userId del destinatario usando la clave `user:name:{username}`
- Se consulta la instancia donde está conectado el usuario con `user:{userId}:connectionInstance`
- Según el resultado, se toma una decisión de ruta

**Claves Redis consultadas:**
- `user:name:{username}` → userId (mapeo username a userId)
- `user:{userId}:connectionInstance` → instanceId (instancia donde está conectado)

**Posibles resultados:**
1. **Destinatario online en la misma instancia**: instanceId coincide con la instancia actual
2. **Destinatario online en otra instancia**: instanceId existe pero es diferente
3. **Destinatario offline**: No existe registro en Redis

---

### Paso 4: Decisión de ruta según estado del destinatario

El sistema enruta el mensaje según el estado de conexión del destinatario.

#### Caso A: Destinatario online en la misma instancia

**Qué ocurre:**
- El `SessionRegistryService` obtiene la sesión WebSocket del destinatario
- El mensaje se envía directamente al destinatario usando la sesión activa
- No se utiliza RabbitMQ ni se involucra a otros servicios

**Ventajas:**
- Entrega inmediata sin latencia adicional
- Menor carga en el sistema de mensajería

**Componentes involucrados:**
- `SessionRegistryService`: Gestiona las sesiones WebSocket activas en la instancia

---

#### Caso B: Destinatario online en otra instancia

**Qué ocurre:**
- El `RabbitMQProducerService` publica el mensaje en el exchange `message.exchange`
- Se usa la routing key igual al instanceId de destino (ejemplo: `instance-1`)
- El mensaje llega a la cola `message.sent.{instanceId}` de la instancia destino
- El `ChatMessageConsumer` en chat-service consume el mensaje
- Chat-service reenvía el mensaje a connection-service de la instancia destino
- Connection-service entrega el mensaje al destinatario vía WebSocket

**Flujo:**
```
connection-service (instancia A)
    ↓ RabbitMQ (message.sent.instanceB)
chat-service (instancia B)
    ↓ REST API
connection-service (instancia B)
    ↓ WebSocket
Cliente destinatario
```

**Componentes involucrados:**
- `RabbitMQProducerService`: Publica mensajes a RabbitMQ
- `ChatMessageConsumer`: Consume mensajes de colas de instancia específica en chat-service

---

#### Caso C: Destinatario offline

**Qué ocurre:**
- El mensaje se publica en la cola `message.offline` mediante `sendToOfflineQueue`
- Se publica un evento de notificación en la cola `message.notification`
- El evento incluye el recipientUserId para que notification-service pueda enviar la notificación SSE

**Publicaciones a RabbitMQ:**
1. **Cola message.offline**: Contiene el mensaje completo para ser procesado por chat-service
2. **Cola message.notification**: Contiene un NotificationEvent para alertar al usuario

**Por qué dos colas:**
- `message.offline`: Permite a chat-service guardar el mensaje en la base de datos
- `message.notification`: Permite a notification-service enviar notificación push inmediata si el usuario tiene conexión SSE activa

---

### Paso 5: chat-service procesa mensaje offline

El `OfflineMessageConsumer` en chat-service consume el mensaje de la cola `message.offline`.

**Qué ocurre:**
- El consumidor recibe el mensaje y lo pasa al sistema de handlers
- `OfflineChatMessageHandler` procesa el mensaje de chat
- Se verifica si el remitente está bloqueado por el destinatario
- Si no está bloqueado, el mensaje se guarda en la base de datos

**Verificación de bloqueo:**
- Se consulta la tabla `blocked_contacts` para verificar si existe un bloqueo
- Si el remitente está bloqueado, el mensaje se descarta y se registra un log

**Persistencia:**
- El mensaje se guarda en la tabla `messages` con estado `seen = false`
- Se registra el messageId, sender, recipient, content y timestamp

**Componentes involucrados:**
- `OfflineMessageConsumer`: Consume mensajes de la cola `message.offline`
- `OfflineChatMessageHandler`: Handler específico para mensajes de chat offline
- `DeliveryService`: Verifica bloqueos y gestiona la persistencia

---

### Paso 6: notification-service recibe evento

El `NotificationConsumer` en notification-service escucha la cola `message.notification`.

**Qué ocurre:**
- El consumidor recibe el `NotificationEvent` de RabbitMQ
- Se extrae el `recipientUserId` del evento
- Se determina el tipo de notificación: `NEW_MESSAGE` o `NEW_IMAGE_MESSAGE`

**Estructura del NotificationEvent:**
```json
{
  "type": "NEW_MESSAGE",
  "messageId": "1234567890",
  "sender": "usuario1",
  "recipient": "usuario2",
  "recipientUserId": "user-uuid-1234",
  "data": "<bytes protobuf>"
}
```

**Componentes involucrados:**
- `NotificationConsumer`: Consume eventos de la cola `message.notification`

---

### Paso 7: SSE envía notificación push

El `SseNotificationService` envía la notificación al cliente mediante SSE.

**Qué ocurre:**
- El servicio busca el `SseEmitter` activo para el `recipientUserId`
- Si existe una conexión SSE activa, se envía el mensaje de notificación
- Si no hay conexión activa, la notificación se pierde (el usuario la recibirá cuando consulte mensajes pendientes)

**Envío SSE:**
- El cliente mantiene una conexión SSE abierta con el endpoint `/api/notifications/subscribe/{userId}`
- El servidor envía eventos de notificación a través de esta conexión
- La conexión se mantiene abierta indefinidamente hasta que el cliente se desconecta

**Manejo de conexiones:**
- `SseNotificationService` mantiene un mapa de `userId → SseEmitter`
- Al enviar una notificación, se busca el emitter y se envía el mensaje
- Si el envío falla, la conexión se elimina del mapa

**Componentes involucrados:**
- `SseNotificationService`: Gestiona conexiones SSE y envía notificaciones
- `SseNotificationController`: Proporciona el endpoint de suscripción SSE

---

### Paso 8: Cliente reconectado recibe pendientes

Cuando un usuario offline se conecta, recupera todos los mensajes pendientes.

**Qué ocurre:**
- El cliente establece conexión SSE con notification-service
- El cliente establece conexión WebSocket con connection-service
- El cliente solicita mensajes pendientes al connection-service
- Connection-service llama a chat-service via REST API
- Chat-service devuelve todos los pendientes en un solo mensaje

**Endpoint REST:**
```
GET /api/v1/messages/pending/{username}
```

**Tipos de pendientes recuperados:**
- Mensajes de texto no leídos
- Mensajes de imagen pendientes
- Solicitudes de eliminación de historial
- Notificaciones de bloqueo/desbloqueo
- Confirmaciones de lectura pendientes
- Actualizaciones de identidad de contacto

**Proceso de limpieza:**
- Una vez entregados los mensajes pendientes, se eliminan de la base de datos
- Esto evita entregas duplicadas cuando el usuario se reconecta múltiples veces

**Componentes involucrados:**
- `MessageService` (chat-service): Recupera todos los tipos de pendientes
- `PendingMessageController` (chat-service): Proporciona el endpoint REST

---

## 3. Formato de Mensajes

### NotificationEvent

Publicado en la cola `message.notification`:

```json
{
  "type": "NEW_MESSAGE",
  "messageId": "1234567890",
  "sender": "usuario1",
  "recipient": "usuario2",
  "recipientUserId": "user-uuid-1234",
  "data": "<bytes binarios del mensaje protobuf>"
}
```

**Tipos posibles:**
- `NEW_MESSAGE`: Notificación de nuevo mensaje de texto
- `NEW_IMAGE_MESSAGE`: Notificación de nuevo mensaje de imagen

---

### DeliveryStatusEvent

Publicado en la cola `message.delivery`:

```json
{
  "type": "READ",
  "messageId": "1234567890",
  "recipient": "usuario2",
  "data": "<bytes binarios del WsMessage con DeliveryStatus>"
}
```

**Tipos posibles:**
- `DELIVERED`: Mensaje entregado al destinatario
- `READ`: Mensaje marcado como leído
- `MESSAGE_DELETED`: Mensaje eliminado
- `BLOCKED`: Usuario bloqueado

---

### RoutedMessage

Publicado en colas `message.offline` y `message.sent.{instanceId}`:

```json
{
  "sender": "usuario1",
  "recipient": "usuario2",
  "content": "<bytes binarios del WsMessage protobuf>",
  "instanceId": "instance-1"
}
```

---

## 4. Estructura de Clases Principales

### connection-service

| Clase | Responsabilidad |
|-------|-----------------|
| `ConnectionMessageDispatcher` | Dispatcher que delega mensajes WebSocket a handlers específicos |
| `ChatMessageHandler` | Procesa mensajes de chat recibidos del cliente WebSocket |
| `MessageRouterService` | Determina la ruta del mensaje según el estado de conexión del destinatario |
| `SessionRegistryService` | Gestiona sesiones WebSocket activas en la instancia actual |
| `RabbitMQProducerService` | Publica mensajes a las colas de RabbitMQ |
| `DeliveryStatusConsumer` | Consume eventos de estado de entrega desde message.delivery |

### chat-service

| Clase | Responsabilidad |
|-------|-----------------|
| `ChatMessageConsumer` | Consume mensajes de la cola message.sent.{instanceId} para usuarios online |
| `OfflineMessageConsumer` | Consume mensajes de la cola message.offline para usuarios offline |
| `OfflineChatMessageHandler` | Guarda mensajes offline en la base de datos |
| `DeliveryService` | Procesa mensajes verificando bloqueos y persistiendo |
| `MessageService` | Gestiona la persistencia y recuperación de mensajes pendientes |

### notification-service

| Clase | Responsabilidad |
|-------|-----------------|
| `NotificationConsumer` | Consume eventos de la cola message.notification |
| `SseNotificationService` | Gestiona conexiones SSE activas y envía notificaciones |
| `SseNotificationController` | Proporciona endpoints REST para suscripciones SSE |

---

## 5. Diagrama del Flujo

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              ENVÍO DE MENSAJE                                │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente emisor]
       │
       │ WebSocket (WsMessage con ChatMessage)
       ▼
┌──────────────────────┐
│ connection-service   │
│                      │
│ 1. Dispatcher recibe │
│ 2. ChatMessageHandler│
│ 3. MessageRouter     │
└──────────────────────┘
       │
       │ Consulta Redis: ¿Dónde está el destinatario?
       ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                           DECISIÓN DE RUTA                                    │
├────────────────────┬────────────────────┬────────────────────────────────────┤
│  Online en misma   │  Online en otra    │         Offline                     │
│     instancia      │     instancia      │                                     │
└────────────────────┴────────────────────┴────────────────────────────────────┘
       │                      │                         │
       │                      │                         │
       ▼                      ▼                         ▼
┌──────────────┐    ┌─────────────────┐      ┌─────────────────────────┐
│ WebSocket    │    │ RabbitMQ        │      │ RabbitMQ                │
│ directo al   │    │ message.sent.   │      │ message.offline         │
│ destinatario │    │ {instanceId}    │      │ + message.notification  │
└──────────────┘    └─────────────────┘      └─────────────────────────┘
       │                      │                         │
       │                      │                         │
       ▼                      ▼                         ▼
┌──────────────┐    ┌─────────────────┐      ┌─────────────────────────┐
│ [Cliente     │    │ chat-service    │      │ chat-service            │
│  destinatario│    │ consume y       │      │ OfflineMessageConsumer  │
│  recibe]     │    │ reenvía a       │      │ guarda en BD            │
└──────────────┘    │ connection-serv │      └─────────────────────────┘
                    └─────────────────┘                 │
                           │                            │
                           ▼                            ▼
                    ┌─────────────────┐      ┌─────────────────────────┐
                    │ connection-serv │      │ notification-service    │
                    │ entrega vía     │      │ NotificationConsumer    │
                    │ WebSocket       │      └─────────────────────────┘
                    └─────────────────┘                 │
                           │                            │
                           ▼                            ▼
                    ┌─────────────────┐      ┌─────────────────────────┐
                    │ [Cliente        │      │ SSE al cliente          │
                    │  destinatario   │      │ (si tiene conexión      │
                    │  recibe]        │      │  SSE activa)            │
                    └─────────────────┘      └─────────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                         RECUPERACIÓN AL CONECTAR                            │
└─────────────────────────────────────────────────────────────────────────────┘

[Usuario offline se conecta]
       │
       ├────────────────────────────────────────┐
       │                                        │
       ▼                                        ▼
┌──────────────────┐                  ┌──────────────────┐
│ Conexión SSE     │                  │ Conexión WebSocket│
│ notification-serv│                  │ connection-serv   │
└──────────────────┘                  └──────────────────┘
                                              │
                                              │ GET /api/v1/messages/pending/{username}
                                              ▼
                                      ┌──────────────────┐
                                      │ chat-service     │
                                      │ MessageService   │
                                      │ devuelve todos   │
                                      │ los pendientes   │
                                      └──────────────────┘
                                              │
                                              ▼
                                      ┌──────────────────┐
                                      │ Cliente recibe   │
                                      │ mensajes         │
                                      │ pendientes       │
                                      └──────────────────┘
```

---

## 6. Resumen

El flujo de notificaciones garantiza que:

1. **Mensajes en tiempo real**: Los mensajes se entregan inmediatamente cuando ambos usuarios están online
2. **Entrega confiable offline**: Los mensajes se persisten cuando el destinatario está offline
3. **Notificaciones push**: El usuario recibe alertas inmediatas de nuevos mensajes mediante SSE
4. **Recuperación de pendientes**: Al reconectarse, el usuario recibe todos los mensajes no entregados
5. **Multi-instancia**: El sistema soporta múltiples instancias de connection-service escalando horizontalmente
