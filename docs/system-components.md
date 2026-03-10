# Diagrama de Componentes - Sistema de Mensajería

## Vista General de la Arquitectura

```
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                                      CLIENTE DESKTOP                                          │
│  ┌─────────────────────────────────┐     ┌──────────────────────────────────────────────┐   │
│  │   WebSocket Binario (Protobuf) │     │           STOMP (Notificaciones)            │   │
│  │   - Mensajes de chat            │     │   - Online/offline contactos                │   │
│  │   - Estados: entrega/visto      │     │   - Contador notificaciones no leídas       │   │
│  └───────────────┬─────────────────┘     └──────────────────┬───────────────────────────┘   │
└──────────────────┼─────────────────────────────────────────┼────────────────────────────────┘
                   │ ws://localhost:8080/ws                    │ ws://localhost:8080/ws-notif.
                   ▼                                           ▼
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│                                   API GATEWAY (:8080)                                        │
│  ┌─────────────────────────────────────────────────────────────────────────────────────────┐   │
│  │  - Autenticación JWT                                                               │   │
│  │  - Routing: /ws/** → connection-service (:8083)                                   │   │
│  │  - Routing: /ws-notifications/** → notification-service (:8084)                  │   │
│  │  - Routing: /api/v1/messages/** → chat-service (:8085)                           │   │
│  └─────────────────────────────────────────────────────────────────────────────────────────┘   │
└──────────────────┬────────────────────────────────────────────────┬───────────────────────────┘
                   │                                                │
                   ▼                                                ▼
┌─────────────────────────────┐    ┌──────────────────────────────────────────────────────────────┐
│  CONNECTION-SERVICE (:8083) │    │            NOTIFICATION-SERVICE (:8084)                    │
│                             │    │                                                              │
│  ┌───────────────────────┐  │    │  ┌──────────────────────────────────────────────────────┐  │
│  │ ConnectionWSHandler  │  │    │  │  STOMP WebSocket Handler                              │  │
│  │ - Recibe mensajes    │  │    │  │  - /topic/notifications/{userId}                     │  │
│  │ - Envía estados      │  │    │  │  - /topic/presence/{userId}                           │  │
│  └───────────┬───────────┘  │    │  │  - /queue/notifications/{userId}                     │  │
│              │              │    │  └──────────────────────┬───────────────────────────────┘  │
│  ┌───────────┴───────────┐  │    │                       │                                  │
│  │ SessionRegistryService│  │    │  ┌─────────────────────┴────────────────────────────┐    │
│  │ - Registra sesiones  │  │    │  │ NotificationService                               │    │
│  │ - Redis: instanceId   │  │    │  │ - Crear notificaciones                            │    │
│  │ - Cleanup sesiones    │  │    │  │ - Actualizar contadores                           │    │
│  └───────────┬───────────┘  │    │  └──────────────────────┬────────────────────────────┘    │
│              │              │    │                           │                                 │
│  ┌───────────┴───────────┐  │    │  ┌────────────────────────┴────────────────────────┐      │
│  │ MessageRouterService │  │    │  │ NotificationConsumer (RabbitMQ)                  │      │
│  │ - Busca del instance │  │    │  │ - message.sent.{instanceId}                     │      │
│  │   destinatario       │  │    │  │ - Genera notificaciones push                     │      │
│  │ - Rutea a offline    │  │    │  └───────────────────────────────────────────────────┘      │
│  │   si no conectado   │  │    │                                                              │
│  └───────────┬───────────┘  │    └──────────────────────────────────────────────────────────────┘
│              │              │                                      │
│  ┌───────────┴───────────┐  │    ┌───────────────────────────────────────────────────────┐
│  │ PendingMessagesService│  │    │                                                      │
│  │ - GET mensajes        │──────►│  CHAT-SERVICE (:8085)                               │
│  │   desde chat-service  │      │  ┌─────────────────────────────────────────────────┐ │
│  └───────────────────────┘      │  │ OfflineMessageConsumer (RabbitMQ)             │ │
│  ┌───────────────────────┐     │  │ - Escucha: message.offline                    │ │
│  │ RabbitMQProducer      │     │  │ - Guarda mensajes offline en BD               │ │
│  │ - Encola a:          │     │  └──────────────┬─────────────────────────────────┘ │
│  │ message.sent.{inst}  │     │                 │                                         │
│  │ message.offline      │────►│  ┌──────────────┴─────────────────────────────────┐ │
│  └───────────────────────┘     │  │ MessageController (REST)                        │ │
│                                 │  │ - GET /api/v1/messages/pending/{username}                                            │ │
│                                 │  └─────────────────────────────────────────────────┘ │
└─────────────────────────────┘   └────────────────────────────────────────────────────────┘
                   │                                   │
                   │     ┌────────────────────────────┴────────────────────────────┐
                   │     │                      RABBITMQ                             │
                   │     │  ┌─────────────────────────────────────────────────────┐ │
                   │     │  │ Exchange: message.exchange (direct)                  │ │
                   │     │  │                                                      │ │
                   │     │  │ ├── message.sent.instance-1 → chat-service-1        │ │
                   │     │  │ ├── message.sent.instance-2 → chat-service-2        │ │
                   │     │  │ ├── message.sent.instance-N → chat-service-N          │ │
                   │     │  │ └── message.offline → chat-service (TODAS)          │ │
                   │     │  │                                                      │ │
                   │     │  │ Exchange: notification (fanout)                      │ │
                   │     │  │                                                      │ │
                   │     │  ├── message.delivery → connection-service           │ │
                   │     │  └── notification.events → notification-service       │ │
                   │     │  └─────────────────────────────────────────────────────┘ │
                   ▼     ▼
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                                  CHAT-SERVICE (:8085)                                        │
│                                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ OfflineMessageConsumer (RabbitMQ) - NUEVO                                             │  │
│  │ - Escucha: message.offline                                                           │  │
│  │ - Recibe mensajes offline y los guarda en BD                                       │  │
│  └─────────────────────────────┬─────────────────────────────────────────────────────┘  │
│                                │                                                              │
│  ┌─────────────────────────────┴───────────────────────────────────────────────────────┐  │
│  │ MessageController (REST) - NUEVO                                                    │  │
│  │ - GET /api/v1/messages/pending/{username}                                           │  │
│  │ - Devuelve WsMessage con TODOS los pendientes en Base64                            │  │
│  └─────────────────────────────┬───────────────────────────────────────────────────────┘  │
│                                │                                                              │
│  ┌─────────────────────────────┴───────────────────────────────────────────────────────┐  │
│  │ MessageService                                                                       │  │
│  │ - saveMessage()                                                                      │  │
│  │ - getUnreadMessages(username)                                                        │  │
│  │ - getAllPendingMessages(username) <- NUEVO                                           │  │
│  │ - getAndClearPendingBlocks() <- NUEVO                                               │  │
│  │ - getAndClearPendingUnblocks() <- NUEVO                                             │  │
│  │ - getAndClearPendingClearHistories() <- NUEVO                                        │  │
│  │ - getAndClearPendingContactIdentities() <- NUEVO                                     │  │
│  └───────────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Flujo de Datos: Envío de Mensaje (Destinatario Online)

```
┌─────────┐    ┌──────────────┐    ┌─────────────────┐    ┌──────────────┐
│ Cliente │───►│connection-   │───►│    RabbitMQ     │───►│ chat-service │
│         │    │ service      │    │ message.sent.{}│    │              │
└─────────┘    └──────────────┘    └─────────────────┘    └──────┬───────┘
                                                                     │
                                      ┌────────────────────────────┘
                                      ▼
                           ┌─────────────────────┐
                           │ DB: mensaje guardado│
                           │ seen=false          │
                           └─────────────────────┘
```

## Flujo de Datos: Envío de Mensaje (Destinatario Offline)

```
┌─────────┐    ┌──────────────┐    ┌─────────────────┐    ┌──────────────┐
│ Cliente │───►│connection-   │───►│    RabbitMQ     │───►│ chat-service │
│  (A)    │    │ service      │    │ message.offline │    │ (offline)    │
└─────────┘    └──────────────┘    └─────────────────┘    └──────┬───────┘
                                                                     │
                                      ┌────────────────────────────┘
                                      ▼
                           ┌─────────────────────┐
                           │ DB: mensaje guardado│
                           │ seen=false          │
                           │ (esperando lectura) │
                           └─────────────────────┘
```

## Flujo de Datos: Recuperación de Mensajes Offline

```
┌─────────┐    ┌──────────────┐    ┌────────────────────────────────┐
│ Cliente │───►│connection-   │───►│ chat-service                   │
│  (B)    │    │ service      │    │ GET /api/v1/messages/pending/{}│
└─────────┘    └──────┬───────┘    └──────────────┬─────────────────┘
                       │                           │
                       │    ┌──────────────────────┘
                       │    ▼
                       │ ┌─────────────────────────────────────┐
                       │ │ WsMessage (Base64)                  │
                       │ │ - UnreadMessagesList                │
                       │ │ - PendingClearHistoryList (deleted) │
                       │ │ - BlockedUsersList                  │
                       │ │ - UnblockedUsersList                │
                       │ │ - PendingClearHistoryList (history) │
                       │ │ - MessagesReadUpdate                │
                       │ │ - ContactIdentity                   │
                       │ └──────────┬──────────────────────────┘
                       │            │
                       └────────────┘
                            │
                            ▼
                     ┌──────────────┐
                     │ WebSocket    │
                     │ WsMessage    │
                     │ (todos los   │
                     │  pendientes) │
                     └──────────────┘
```

---

## Flujo de Datos: Estados de Mensaje (Entrega/Visto)

```
┌──────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ chat-service │───►│    RabbitMQ     │───►│ connection-    │
│ (marca estado)│    │ message.delivery │    │ service        │
└──────────────┘    └─────────────────┘    └────────┬────────┘
                                                     │
                                                     ▼
                                              ┌─────────────────┐
                                              │ Cliente         │
                                              │ (WebSocket bin) │
                                              └─────────────────┘
```

---

## Redis: Estructura de Datos

```
# Sesiones de usuarios
user:{userId}:sessions                    → [sessionId1, sessionId2]
session:{sessionId}:user                   → {userId}
session:{sessionId}:username               → {username}

# Mapping usuario → instancia de conexión
user:{userId}:connectionInstance           → {instanceId}

# Instancias activas del connection-service
connection:instance:{instanceId}:sessions  → [sessionId1, sessionId2]
```

---

## Componentes del Cliente Desktop

```
┌────────────────────────────────────────────────────────────────────────┐
│                      WEBSOCKET CLIENT                                   │
│                                                                         │
│  ┌─────────────────────┐          ┌─────────────────────────────────┐ │
│  │ WebSocketService    │          │ NotificationSTOMPService       │ │
│  │ - Conexión binaria │          │ - Conexión STOMP                │ │
│  │ - Enviar mensajes   │          │ - Suscribir a notificaciones   │ │
│  │ - Recibir estados  │          │ - Recibir presencia            │ │
│  └─────────────────────┘          └─────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Puertos de Servicios

| Servicio | Puerto | Protocolo |
|-----------|--------|-----------|
| API Gateway | 8080 | HTTP/WS |
| profile-service | 8088 | REST |
| chat-service | 8085 | RabbitMQ (consumidor) + REST |
| notification-service | 8084 | STOMP WebSocket |
| connection-service | 8083 | WebSocket Binario |
| Redis | 6379 | - |
| RabbitMQ | 5672 | - |

---

## Colas RabbitMQ

```
message.exchange (direct)
│
├── message.sent.instance-1     → chat-service-instance-1
├── message.sent.instance-2     → chat-service-instance-2
├── message.sent.instance-N     → chat-service-instance-N
├── message.offline             → TODAS las instancias de chat-service
├── message.delete              → TODAS las instancias de chat-service
├── message.clear_history       → TODAS las instancias de chat-service
└── message.delivery           → connection-service

notification.exchange (fanout)
│
├── message.delivery            → connection-service
└── notification.events         → notification-service
```

---

## Sistema de Handlers (Patrón de Diseño)

### connection-service: ConnectionMessageDispatcher

El connection-service usa un patrón de dispatch con handlers para procesar diferentes tipos de mensajes WebSocket. Este patrón permite agregar nuevos tipos de mensajes sin modificar el código existente.

```
ConnectionWebSocketHandler.handleBinaryMessage()
    ↓
ConnectionMessageDispatcher.dispatch()
    ↓
┌─────────────┬───────────────┬──────────────────┐
│ ChatMessage │ DeleteMessage │ ClearHistory     │
│ Handler     │ Handler       │ Handler          │
└─────────────┴───────────────┴──────────────────┘
    ↓              ↓               ↓
routeMessage()  routeDeletion()  routeClearHistory()
    ↓              ↓               ↓
RabbitMQ       RabbitMQ          RabbitMQ
```

### chat-service: OfflineMessageDispatcher

El chat-service usa un patrón de dispatch con handlers para procesar mensajes offline recibidos desde RabbitMQ. Este patrón permite agregar nuevos tipos de mensajes sin modificar el código existente.

```
OfflineMessageConsumer.handleOfflineMessage()
    ↓
OfflineMessageDispatcher.dispatch()
    ↓
┌─────────────────────┬─────────────────────┬─────────────────────┐
│ OfflineChatMessage │ OfflineDeleteMessage│ OfflineClearHistory │
│ Handler            │ Handler             │ Handler             │
└─────────────────────┴─────────────────────┴─────────────────────┘
┌─────────────────────┬─────────────────────┬─────────────────────┐
│ OfflineBlockContact│ OfflineUnblockCont │ OfflineMarkAsRead  │
│ Handler            │ Handler            │ Handler            │
└─────────────────────┴─────────────────────┴─────────────────────┘
┌─────────────────────┐
│ OfflineContactIden │
│ Handler            │
└─────────────────────┘
    ↓              ↓               ↓          ↓          ↓
save to DB    save to DB     save to DB   save to DB  save to DB
```

---

## Flujo de Eliminación de Mensaje "Para Todos"

```
┌──────────┐     ┌─────────────────┐     ┌─────────┐     ┌────────────────┐
│ Cliente  │────►│connection-      │────►│RabbitMQ │────►│ chat-service   │
│ (A)      │     │ service         │     │ message.│     │ DeleteMessage  │
│ elimina  │     │ ChatMessage     │     │ delete  │     │ Consumer       │
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
              │ RabbitMQ           │          │ RabbitMQ             │   │ PendingDeletion   │
              │ message.delivery   │          │ message.delivery    │   │ (guardar en BD)   │
              │ tipo: MESSAGE_DELETED│         │ tipo: MESSAGE_DELETED│   │                    │
              └─────────┬───────────┘          └──────────┬──────────┘   └────────────────────┘
                        │                                 │
                        ▼                                 ▼
              ┌─────────────────────────────────────────────────────────┐
              │              connection-service                          │
              │  DeliveryStatusConsumer.handleDeliveryStatus()          │
              │    - Detecta tipo "MESSAGE_DELETED"                   │
              │    - Reenvía al cliente via WebSocket                │
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

---

## Endpoints REST

| Servicio | Endpoint | Método | Descripción |
|----------|----------|--------|-------------|
| chat-service | `/api/v1/messages/pending/{username}` | GET | Obtiene TODOS los mensajes pendientes en Base64 (WsMessage con mensajes, eliminaciones, bloqueos, desbloqueos, historial limpiado, confirmaciones de lectura, identidades de contacto) |

