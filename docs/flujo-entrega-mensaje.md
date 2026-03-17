# Flujo: Entrega de Mensaje

## Resumen

Este documento describe el proceso completo mediante el cual un usuario envía un mensaje a otro en el sistema de mensajería. El flujo involucra el servicio de conexiones (connection-service) para manejo de WebSocket, Redis para verificación de presencia, RabbitMQ para mensajería asíncrona, y el servicio de chat para persistencia de mensajes.

## Precondiciones

- El usuario debe estar autenticado en la aplicación
- El contacto destinatario debe existir en la lista de contactos del remitente
- El remitente no debe tener bloqueado al destinatario (y viceversa)

## Flujo Principal

### Paso 1: Interfaz de Usuario (JavaFX)

El usuario escribe y envía un mensaje de texto desde la interfaz de chat.

**Componente:** `ChatController`  
**Ubicación:** `websocket-client/src/main/java/com/pola/controller/ChatController.java`

El controlador delega el envío al `MessageSender`.

### Paso 2: Construcción del Mensaje Protobuf

El `MessageSender` construye un mensaje Protobuf de tipo `ChatMessage`.

**Componente:** `MessageSender`  
**Ubicación:** `websocket-client/src/main/java/com/pola/service/MessageSender.java`

```java
public void sendTextMessage(long id, String content, String sender, String recipient) {
    MessagesProto.ChatMessage chatMessage = MessagesProto.ChatMessage.newBuilder()
        .setId(String.valueOf(id))
        .setType(MessageType.TEXT)
        .setSender(sender) // username del remitente
        .setRecipient(recipient) // username del destinatario
        .setContent(content)
        .setTimestamp(Instant.now().toEpochMilli())
        .build();

    sendMessage(WsMessage.newBuilder().setChatMessage(chatMessage).build());
}
```

### Paso 3: Envío via WebSocket Binario

El mensaje Protobuf se envía al **connection-service** mediante WebSocket binario.

**Componente:** `WebSocketServiceImpl`  
**Ubicación:** `websocket-client/src/main/java/com/pola/service/WebSocketServiceServiceImpl.java`

```java
@Override
public void onMessage(Session session, BinaryMessage message) {
    // Protobuf parsing and routing
    String sender = sessionInfo.getUsername();
    messageRouterService.routeMessage(sender, recipient, data);
}
```

### Paso 4: Routing y Verificación de Presencia (connection-service)

El **connection-service** verifica si el destinatario está online mediante Redis.

**Componente:** `MessageRouterService`  
**Ubicación:** `connection-service/src/main/java/com/basic_chat/connection_service/service/MessageRouterService.java`

```java
public void routeMessage(String sender, String recipient, byte[] messageData) {
    // 1. Verificar si destinatario está conectado
    String recipientInstance = sessionRegistryService.getConnectionInstance(recipient);
    
    if (recipientInstance == null) {
        // Destinatario offline - enviar a cola de mensajes offline
        log.debug("Destinatario {} no está conectado, encolando mensaje en cola offline", recipient);
        rabbitMQProducerService.sendToOfflineQueue(new RoutedMessage(sender, recipient, messageData, null));
        return;
    }
    
    if (recipientInstance.equals(instanceId)) {
        // Destinatario online en esta instancia - enviar directamente
        sessionRegistryService.sendToUserByUsername(recipient, messageData);
    } else {
        // Destinatario online en otra instancia - encolar para esa instancia
        rabbitMQProducerService.sendToQueue(recipientInstance, new RoutedMessage(sender, recipient, messageData, recipientInstance));
    }
}
```

**Claves Redis involucradas:**
- `user:{userId}:connectionInstance` → `{instanceId}` (instancia donde está conectado)
- `user:name:{username}` → `{userId}` (mapeo username → userId)

### Paso 5a: Entrega en Tiempo Real (Destinatario Online)

Si el destinatario está online en la misma instancia, el mensaje se envía directamente por WebSocket.

**Componente:** `SessionRegistryService`  
**Ubicación:** `connection-service/src/main/java/com/basic_chat/connection_service/service/SessionRegistryService.java`

```java
public void sendToUserByUsername(String username, byte[] data) {
    // Buscar sesión del usuario y enviar mensaje
    String userId = redisTemplate.opsForValue().get(USER_NAME_PREFIX + username);
    if (userId != null) {
        sendToUser(userId, data);
    }
}
```

### Paso 5b: Encolar Mensaje Offline (Destinatario Offline)

Si el destinatario está offline, el mensaje se envía a la cola `message.offline`.

**Componente:** `RabbitMQProducerService`  
**Ubicación:** `connection-service/src/main/java/com/basic_chat/connection_service/service/RabbitMQProducerService.java`

```java
public void sendToOfflineQueue(RoutedMessage message) {
    log.debug("Encolando mensaje offline para {}", message.getRecipient());
    rabbitTemplate.convertAndSend(
        RabbitMQConfig.MESSAGE_EXCHANGE,
        RabbitMQConfig.OFFLINE_ROUTING_KEY, // "offline"
        message
    );
}
```

### Paso 6: Procesamiento de Mensajes Offline (chat-service)

El **chat-service** consume mensajes de la cola offline y los guarda en la base de datos.

**Componente:** `OfflineMessageConsumer`  
**Ubicación:** `chat-service/src/main/java/com/basic_chat/chat_service/consumer/OfflineMessageConsumer.java`

```java
@Component
@Slf4j
public class OfflineMessageConsumer {
    
    @RabbitListener(queues = "message.offline")
    public void handleOfflineMessage(RoutedMessageEvent event) {
        log.info("Received offline message from {} to {}", event.getSender(), event.getRecipient());
        
        try {
            MessagesProto.WsMessage wsMessage = MessagesProto.WsMessage.parseFrom(event.getContent());
            
            if (wsMessage.hasChatMessage()) {
                MessagesProto.ChatMessage chatMessage = wsMessage.getChatMessage();
                deliveryService.processMessage(wsMessage, chatMessage);
            }
        } catch (Exception e) {
            log.error("Error processing offline message", e);
        }
    }
}
```

### Paso 7: Persistencia del Mensaje (MySQL)

Independientemente de si el destinatario está online o offline, el mensaje se guarda en la base de datos.

**Componente:** `MessageService`  
**Ubicación:** `chat-service/src/main/java/com/basic_chat/chat_service/service/MessageService.java`

```java
@Transactional
public void saveMessage(MessagesProto.ChatMessage message) {
    Message mappedMessage = mapProtobufToEntity(message);
    messageRepository.save(mappedMessage);
    log.info("Mensaje guardado messageId: {}", mappedMessage.getId());
}
```

### Paso 8: Notificación de Entrega

Cuando el mensaje se guarda, se envía una notificación de estado "DELIVERED".

**Componente:** `DeliveryService`  
**Ubicación:** `chat-service/src/main/java/com/basic_chat/chat_service/service/DeliveryService.java`

```java
private void sendDeliveryStatus(String recipient, String type, String messageId, byte[] messageData) {
    DeliveryStatusEvent event = new DeliveryStatusEvent();
    event.setType(type); // "DELIVERED"
    event.setMessageId(messageId);
    event.setRecipient(recipient);
    event.setData(messageData);
    
    rabbitTemplate.convertAndSend("message.exchange", "delivery", event);
}
```

### Paso 9: Recepción de Notificación (connection-service)

El **connection-service** recibe la notificación de entrega y la reenvía al remitente.

**Componente:** `DeliveryStatusConsumer`  
**Ubicación:** `connection-service/src/main/java/com/basic_chat/connection_service/consumer/DeliveryStatusConsumer.java`

```java
@RabbitListener(queues = "message.delivery")
public void handleDeliveryStatus(DeliveryStatusEvent event) {
    String recipient = event.getRecipient();
    sessionRegistryService.sendToUserByUsername(recipient, event.getData());
}
```

### Paso 10: Recepción en Cliente

El cliente recibe la notificación de entrega vía WebSocket.

**Componente:** `WebSocketServiceImpl`  
**Ubicación:** `websocket-client/src/main/java/com/pola/service/WebSocketServiceImpl.java`

```java
// El cliente procesa el mensaje recibido
switch (wsMessage.getPayloadCase()) {
    case CHAT_MESSAGE:
        handleChatMessage(wsMessage.getChatMessage());
        break;
    case CHAT_MESSAGE_RESPONSE:
        handleMessageResponse(wsMessage.getChatMessageResponse());
        break;
    // ...
}
```

## Flujo Alternativo: Recuperación de Mensajes Offline

Cuando un usuario se conecta y tiene mensajes pendientes:

### Paso A1: Conexión del Cliente

El cliente se conecta al **connection-service**.

### Paso A2: Obtención de Mensajes Pendientes

El **connection-service** solicita mensajes pendientes al **chat-service**.

**Componente:** `PendingMessagesService`  
**Ubicación:** `connection-service/src/main/java/com/basic_chat/connection_service/service/PendingMessagesService.java`

```java
public List<byte[]> getPendingMessages(String username) {
    String url = chatServiceUrl + "/api/v1/messages/unread/" + username;
    Map<String, Object> response = restTemplate.getForObject(url, Map.class);
    
    // Procesar respuesta y devolver mensajes
    List<String> encodedMessages = (List<String>) response.get("messages");
    return encodedMessages.stream()
        .map(Base64.getDecoder()::decode)
        .toList();
}
```

### Paso A3: Endpoint REST en chat-service

El **chat-service** expone un endpoint REST para obtener mensajes no leídos.

**Componente:** `MessageController`  
**Ubicación:** `chat-service/src/main/java/com/basic_chat/chat_service/controller/MessageController.java`

```java
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {
    
    @GetMapping("/unread/{username}")
    public ResponseEntity<Map<String, Object>> getUnreadMessages(@PathVariable String username) {
        List<MessagesProto.ChatMessage> messages = messageService.getUnreadMessages(username);
        
        List<String> encodedMessages = messages.stream()
            .map(msg -> Base64.getEncoder().encodeToString(msg.toByteArray()))
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "count", messages.size(),
            "messages", encodedMessages
        ));
    }
}
```

### Paso A4: Envío de Mensajes Pendientes

El **connection-service** envía los mensajes pendientes al cliente por WebSocket.

**Componente:** `ConnectionWebSocketHandler`  
**Ubicación:** `connection-service/src/main/java/com/basic_chat/connection_service/handler/ConnectionWebSocketHandler.java`

```java
@Override
public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    // ... registro de sesión ...
    
    // Enviar mensajes pendientes
    sendPendingMessages(session, username);
}

private void sendPendingMessages(WebSocketSession session, String username) {
    List<byte[]> pendingMessages = pendingMessagesService.getPendingMessages(username);
    
    for (byte[] messageData : pendingMessages) {
        if (session.isOpen()) {
            session.sendMessage(new BinaryMessage(messageData));
        }
    }
}
```

## Componentes Participantes

| Componente | Ubicación | Tecnología | Responsabilidad |
|------------|-----------|------------|-----------------|
| ChatController | websocket-client/.../controller/ | JavaFX/FXML | Interfaz de usuario |
| MessageSender | websocket-client/.../service/ | Java/Protobuf | Construir mensaje Protobuf |
| WebSocketServiceImpl | websocket-client/.../service/ | Jakarta WebSocket | Envío al connection-service |
| ConnectionWebSocketHandler | connection-service/.../handler/ | Spring WebSocket | Recepción y routing |
| MessageRouterService | connection-service/.../service/ | Spring Boot | Decidir ruta del mensaje |
| SessionRegistryService | connection-service/.../service/ | Spring Data Redis | Verificar presencia online |
| RabbitMQProducerService | connection-service/.../service/ | Spring AMQP | Encolar mensajes |
| PendingMessagesService | connection-service/.../service/ | RestTemplate | Obtener mensajes pendientes |
| OfflineMessageConsumer | chat-service/.../consumer/ | Spring AMQP | Consumir cola offline |
| MessageController | chat-service/.../controller/ | Spring REST | Endpoint mensajes no leídos |
| MessageService | chat-service/.../service/ | Spring Data JPA | Persistencia en MySQL |
| DeliveryService | chat-service/.../service/ | Spring AMQP | Enviar notificaciones de entrega |
| DeliveryStatusConsumer | connection-service/.../consumer/ | Spring AMQP | Recibir notificaciones |

## Almacenamiento

### connection-service (Redis)
- Clave: `user:{userId}:connectionInstance` → `{instanceId}`
- Propósito: Saber en qué instancia está conectado un usuario

### chat-service (MySQL)
- Tabla: `messages`
- Propósito: Almacenar todos los mensajes enviados
- Contiene: remitente, destinatario, contenido, timestamp, estado (seen)
- Índice: `toUserId + seen=false` para consultas de mensajes no leídos

### notification-service (Redis)
- Clave: `user:name:{username}` → `{userId}`
- Clave: `user:{userId}:sessions` → lista de sessionIds
- Propósito: Gestionar sesiones STOMP (notificaciones push)

### websocket-client (SQLite)
- Tabla: `messages`
- Propósito: Almacenar mensajes localmente para el usuario
- Sincronizado con el servidor

## Colas RabbitMQ

| Cola | Routing Key | Descripción |
|------|-------------|-------------|
| `message.sent.{instanceId}` | `{instanceId}` | Mensajes para instancia específica de chat-service |
| `message.offline` | `offline` | Mensajes de destinatarios offline (escuchada por todas las instancias) |
| `message.delivery` | `delivery` | Notificaciones de estado de entrega |

## Flujo Alternativo: Mensaje Bloqueado

Si el destinatario tiene bloqueado al remitente:

1. `BlockService.isBlocked()` retorna `true`
2. Se envía un `ChatMessageResponse` con `FailureCause.BLOCKED`
3. El cliente muestra un error al usuario

## Notas Adicionales

- El **connection-service** (puerto 8083) es ahora el único punto de entrada WebSocket
- El **chat-service** (puerto 8085) ya no acepta conexiones WebSocket directas
- RabbitMQ gestiona la comunicación entre servicios de forma asíncrona
- La cola `message.offline` es escuchada por **todas** las instancias de chat-service
- El endpoint REST `/api/v1/messages/unread/{username}` permite recuperar mensajes offline
- Redis es la fuente de verdad para determinar si un usuario está conectado
