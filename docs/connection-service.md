# Connection Service

## Descripción General

El **connection-service** es un microservicio Spring Boot que actúa como el centro neurálgico de la comunicación en tiempo real de esta aplicación de mensajería. Su función principal es establecer y mantener conexiones WebSocket持久entescon los clientes de escritorio (JavaFX), permitiendo el intercambio instantáneo de mensajes sin necesidad de polling o solicitudes HTTP repetitivas.

Este servicio implementa una arquitectura distribuida preparada para escalar horizontalmente. Cada instancia del connection-service se identifica mediante un `INSTANCE_ID` único que le permite comunicarse con otras instancias a través de RabbitMQ. Cuando un usuario se conecta, el servicio valida sus credenciales mediante las cabeceras `X-User-ID` y `X-Username`, registra su sesión en Redis con información de la instancia donde está conectado, y recupera cualquier mensaje pendiente que haya recibido mientras estaba desconectado mediante una llamada REST al chat-service. 

El sistema gestiona dinámicamente el estado online/offline de los usuarios: si el destinatario está conectado en la misma instancia, el mensaje se envía directamente por WebSocket; si está en otra instancia, se publica en RabbitMQ para que la instancia correcta lo entregue; y si está desconectado, el mensaje se envía a la cola `message.offline` para que el chat-service lo almacene en la base de datos. Además, el servicio escucha eventos de entrega y lectura en RabbitMQ para reenviar los recibos al remitente original a través de su conexión WebSocket activa.

## Propósito del Servicio

| Atributo | Valor |
|----------|-------|
| Puerto | 8083 |
| Protocolo | WebSocket (ws://localhost:8083/ws) |
| Tipo | Spring Boot Microservice |
| Dependencias Externas | Redis, RabbitMQ, chat-service |

El connection-service cumple cuatro funciones críticas en el ecosistema:

1. **Gestión de Conexiones**: Acepta, valida y mantiene conexiones WebSocket persistentes con los clientes desktop.
2. **Presencia de Usuarios**: Almacena y consulta el estado online/offline de usuarios en Redis.
3. **Enrutamiento de Mensajes**: Determina la ruta óptima para cada mensaje (directo, entre instancias, u offline).
4. **Recuperación de Pendientes**: Obtiene y entrega mensajes no leídos al momento de la conexión.

## Mapa de Componentes

### Clase Principal

| Componente | Tipo | Responsabilidad |
|------------|------|-----------------|
| `ConnectionWebSocketHandler` | @Component | Maneja el ciclo de vida completo de conexiones WebSocket: conexión, mensaje, y desconexión. Delega el procesamiento de mensajes al dispatcher. |
| `ConnectionMessageDispatcher` | @Service | Dispatcher central que distribuye mensajes al handler apropiado según el tipo de mensaje (patrón Chain of Responsibility). |
| `SessionRegistryService` | @Service | Gestiona el registro de sesiones de usuario en Redis. Mantiene mapeos entre userId, username, sessionId, e instanceId. Provee métodos para consultar si un usuario está online y en qué instancia. |
| `MessageRouterService` | @Service | Lógica central de enrutamiento. Determina si el destinatario está online (misma instancia, otra instancia, u offline) y delega al Producer apropiado. |
| `PendingMessagesService` | @Service | Almacena en caché la URL del chat-service y ejecuta llamadas REST `GET /api/v1/messages/pending/{username}` para obtener TODOS los mensajes pendientes. Convierte respuestas Base64 a bytes y los entrega al cliente via WebSocket. Devuelve un solo WsMessage con todos los tipos de pendientes. |
| `RabbitMQProducerService` | @Service | Abstrae la publicación de mensajes a RabbitMQ. Maneja múltiples escenarios: mensaje para otra instancia, mensaje para usuario offline, eliminación de mensajes, y borrado de historial. |
| `DeliveryStatusConsumer` | @Component | Listener de RabbitMQ que escucha la cola `message.delivery`. Reenvía receipts de entrega/lectura y notificaciones de eliminación al usuario correspondiente vía WebSocket. |
| `RoutedMessage` | Model | POJO que representa un mensaje a rutear: remitente, destinatario, contenido, y tipo de mensaje. |
| `DeliveryStatusEvent` | Model | Evento de estado de entrega: messageId, tipo (DELIVERED, READ, MESSAGE_DELETED), destinatario, y datos. |

### Handlers de Mensajes (Patrón de Diseño)

El connection-service implementa un patrón de dispatch con handlers para procesar diferentes tipos de mensajes WebSocket. Este patrón permite agregar nuevos tipos de mensajes sin modificar el código existente.

| Handler | Tipo de Mensaje | Responsabilidad |
|---------|----------------|-----------------|
| `ConnectionWsMessageHandler` | Interfaz | Define el contrato para handlers: `supports(message)` y `handle(sender, message)` |
| `ChatMessageHandler` | ChatMessage | Enruta mensajes de chat al destinatario via MessageRouterService |
| `DeleteMessageHandler` | DeleteMessageRequest | Enruta solicitudes de eliminación de mensaje a la cola `message.delete` |
| `ClearHistoryHandler` | ClearHistoryRequest | Enruta solicitudes de borrado de historial a la cola `message.clear_history` |

### Configuraciones

| Componente | Tipo | Responsabilidad |
|------------|------|-----------------|
| `WebSocketConfig` | @Configuration | Registra el endpoint WebSocket en `/ws` y configura el handler personalizado. Define políticas de ping/pong y tamaño de mensaje. |
| `RedisConfig` | @Configuration | Configura la conexión a Redis mediante StringRedisTemplate. Define serialización de claves y valores. |
| `RabbitMQConfig` | @Configuration | Define el exchange `message.exchange` (direct) y las colas: `message.sent.{instanceId}`, `message.offline`, `message.delete`, `message.clear_history`, y `message.delivery`. Asocia bindings y rutas. |

### Estructura de Paquetes

```
com.basic_chat.connection_service/
├── ConnectionServiceApplication.java
├── config/
│   ├── RabbitMQConfig.java
│   ├── RedisConfig.java
│   └── WebSocketConfig.java
├── consumer/
│   └── DeliveryStatusConsumer.java
├── handler/
│   ├── ConnectionWsMessageHandler.java    (interfaz)
│   ├── ConnectionWebSocketHandler.java
│   ├── ChatMessageHandler.java
│   ├── DeleteMessageHandler.java
│   └── ClearHistoryHandler.java
├── models/
│   ├── DeliveryStatusEvent.java
│   └── RoutedMessage.java
└── service/
    ├── ConnectionMessageDispatcher.java
    ├── MessageRouterService.java
    ├── PendingMessagesService.java
    ├── RabbitMQProducerService.java
    └── SessionRegistryService.java
```
com.basic_chat.connection_service/
├── ConnectionServiceApplication.java
├── config/
│   ├── RabbitMQConfig.java
│   ├── RedisConfig.java
│   └── WebSocketConfig.java
├── consumer/
│   └── DeliveryStatusConsumer.java
├── handler/
│   └── ConnectionWebSocketHandler.java
├── models/
│   ├── DeliveryStatusEvent.java
│   └── RoutedMessage.java
└── service/
    ├── MessageRouterService.java
    ├── PendingMessagesService.java
    ├── RabbitMQProducerService.java
    └── SessionRegistryService.java
```

## Mapa de Flujo

### Flujo 1: Conexión de Usuario

```
┌──────────────┐     ┌────────────────────┐     ┌─────────────┐     ┌────────────────┐     ┌───────────────┐
│  JavaFX      │     │ ConnectionWebSocket│     │  Session    │     │ PendingMessages│     │    Redis      │
│  Client      │────→│ Handler            │────→│ Registry    │────→│ Service        │     │  (Presencia)  │
└──────────────┘     └────────────────────┘     └─────────────┘     └────────────────┘     └───────────────┘
                            │                        │                       │                      │
                            │ validate headers      │                       │                      │
                            │ X-User-ID, X-Username  │                       │                      │
                            │                       │ register session      │                      │
                            │                       │ user:{id}:connection  │                      │
                            │                       │ user:name:{username}  │                      │
                            │                       │ session:{id}:user     │                      │
                             │                       │                       │ GET /pending/{user}   │
                            │                       │                       │                      │
                            │                       │                       │              chat-service
```

**Pasos:**
1. Cliente JavaFX establece conexión WebSocket a `ws://localhost:8083/ws`
2. Handler valida cabecera `X-User-ID` y `X-Username`
3. SessionRegistryService registra sesión en Redis
4. PendingMessagesService llama a chat-service para obtener mensajes pendientes
5. Mensajes pendientes se envían al cliente via WebSocket
6. chat-service marca mensajes como entregados y los elimina de la cola

---

### Flujo 2: Envío de Mensaje (Usuario Online - Misma Instancia)

```
┌──────────────┐     ┌────────────────────┐     ┌────────────────┐     ┌──────────────┐
│  Remitente  │────→│ ConnectionWebSocket│────→│ MessageRouter │────→│  Destinatario│
│  (Client A) │     │ Handler            │     │ Service       │     │  (Client B)  │
└──────────────┘     └────────────────────┘     └────────────────┘     └──────────────┘
                            │                        │
                            │ binario Protobuf       │ lookup Redis
                            │                        │ user:{id}:connection
                            │                        │
                            │─────────── NO ─────────┤ (misma instancia)
                            │                        │
                            │                  send directly
                            │                  via WebSocket
                            │                  session
```

**Pasos:**
1. Cliente A envía mensaje binario Protobuf por WebSocket
2. Handler recibe y extrae recipientId del mensaje
3. MessageRouterService consulta Redis: ¿está el destinatario en esta instancia?
4. Si está en la misma instancia → entrega directa via session.sendMessage()
5. Mensaje llega al cliente B en tiempo real

---

### Flujo 3: Envío de Mensaje (Usuario Online - Otra Instancia)

```
┌──────────────┐     ┌────────────────────┐     ┌────────────────┐     ┌─────────────┐
│  Remitente  │────→│ ConnectionWebSocket│────→│ MessageRouter │────→│  RabbitMQ  │
│  (Client A) │     │ Handler            │     │ Service       │     │            │
└──────────────┘     └────────────────────┘     └────────────────┘     └──────┬──────┘
                                                                            │
                              lookup Redis: user:{id}:connection            │
                              returns: instance-2                           │
                                                                            │
                    ┌───────────────────────────────────────────────────────┘
                    │
                    ▼
            ┌─────────────┐     ┌────────────────────┐     ┌──────────────┐
            │  message.   │────→│ ConnectionService  │────→│  Destinatario│
            │  sent.{id2} │     │  (instance-2)       │     │  (Client B)  │
            └─────────────┘     └────────────────────┘     └──────────────┘
```

**Pasos:**
1. Cliente A envía mensaje por WebSocket
2. Handler recibe mensaje
3. MessageRouterService consulta Redis y descubre que el destinatario está en otra instancia (instance-2)
4. RabbitMQProducerService publica mensaje en cola `message.sent.{instanceId}`
5. La instancia destino consume el mensaje de su cola específica
6. Entrega directa al cliente B via WebSocket

---

### Flujo 4: Envío de Mensaje (Usuario Offline)

```
┌──────────────┐     ┌────────────────────┐     ┌────────────────┐     ┌─────────────┐
│  Remitente  │────→│ ConnectionWebSocket│────→│ MessageRouter │────→│  RabbitMQ  │
│  (Client A) │     │ Handler            │     │ Service       │     │            │
└──────────────┘     └────────────────────┘     └────────────────┘     └──────┬──────┘
                                                                            │
                              lookup Redis: user:{id}:connection           │
                              returns: null (offline)                      │
                                                                            │
                    ┌───────────────────────────────────────────────────────┘
                    │
                    ▼
            ┌─────────────┐     ┌────────────────────┐     ┌─────────────┐
            │  message.   │────→│  chat-service      │────→│    MySQL    │
            │  offline    │     │  (Consumer)         │     │  (Almacena) │
            └─────────────┘     └────────────────────┘     └─────────────┘
```

**Pasos:**
1. Cliente A envía mensaje por WebSocket
2. Handler recibe mensaje
3. MessageRouterService consulta Redis: usuario offline (no hay entrada)
4. RabbitMQProducerService publica en cola `message.offline`
5. chat-service consume el mensaje y lo persiste en MySQL
6. El mensaje queda guardado para cuando el destinatario se conecte

---

### Flujo 5: Estados de Entrega (Delivery Receipts)

```
┌──────────────┐     ┌─────────────┐     ┌────────────────────┐     ┌──────────────┐
│ Destinatario │────→│chat-service │────→│     RabbitMQ       │────→│ConnectionSvc │
│  (Client B)  │     │             │     │ (message.delivery)│     │ (Consumer)  │
└──────────────┘     └─────────────┘     └────────────────────┘     └──────┬───────┘
                                                                           │
                              DeliveryStatusConsumer                       │
                              lookup: user:{senderId}:connection           │
                                                                           │
                    ┌───────────────────────────────────────────────────────┘
                    │
                    ▼
            ┌─────────────┐
            │  Remitente  │
            │  (Client A) │
            │  Receipt    │
            └─────────────┘
```

**Pasos:**
1. Cliente B recibe mensaje → chat-service actualiza estado a DELIVERED
2. chat-service publica evento en `message.delivery`
3. DeliveryStatusConsumer del connection-service recibe el evento
4. Consulta Redis para encontrar la sesión del remitente original
5. Envía receipt (DELIVERED/READ) via WebSocket al remitente

---

## Integraciones

### Redis (Puerto 6379)

Almacena información de presencia y sesiones:

| Clave | Valor | Descripción |
|-------|-------|-------------|
| `user:{userId}:connectionInstance` | instanceId | Instancia donde está conectado el usuario |
| `user:name:{username}` | userId | Mapping username → userId |
| `session:{sessionId}:user` | userId | Sesión → usuario |
| `session:{sessionId}:username` | username | Sesión → username |
| `user:{userId}:sessionId` | sessionId | Usuario → sesión activa |

### RabbitMQ (Puerto 5672)

Colas y exchanges para mensajería distribuida:

```
message.exchange (direct)
├── message.sent.{instanceId}     → Mensajes para usuarios online en esta instancia
├── message.offline                → Mensajes para usuarios offline (procesado por chat-service)
├── message.delete                 → Solicitudes de eliminación de mensajes (procesado por chat-service)
├── message.clear_history          → Solicitudes de borrado de historial (procesado por chat-service)
└── message.delivery              → Receipts de entrega/lectura y notificaciones de eliminación
```

### chat-service (Puerto 8085)

| Endpoint | Método | Propósito |
|----------|--------|-----------|
| `/api/v1/messages/pending/{username}` | GET | Obtener TODOS los mensajes pendientes del usuario (mensajes, eliminaciones, bloqueos, desbloqueos, historial limpiado, confirmaciones de lectura, identidades de contacto) |

Llamado automáticamente cuando un usuario se conecta para recuperar mensajes recibidos mientras estaba offline.

---

## Notas de Arquitectura

- **Modelo de sesión activa**: El sistema implementa una sesión activa por usuario. Si un usuario se conecta desde otro dispositivo, la sesión anterior se sobrescribe en Redis.
- **No está en Docker Compose**: El connection-service corre localmente junto al cliente desktop, no como contenedor Docker.
- **Soporte multi-instancia**: Cada instancia tiene un `INSTANCE_ID` único que permite el enrutamiento entre instancias via RabbitMQ.
- **Serialización binaria**: Usa Protobuf para mensajes eficientes en tamaño y procesamiento.
