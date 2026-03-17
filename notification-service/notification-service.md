# Capacidades de Negocio

## Responsabilidad principal
Este servicio gestiona y distribuye notificaciones en tiempo real para la aplicación de mensajería. Resuelve el problema de informar a los usuarios sobre eventos relevantes (mensajes nuevos, contactos añadidos, cambios de estado, etc.) de manera eficiente y escalable, utilizando WebSocket y RabbitMQ para comunicación instantánea.

## Bounded Context (Contexto Delimitado)
Pertenece al contexto de "Notificaciones" dentro del dominio de mensajería. Según DDD, su responsabilidad está delimitada a la gestión, almacenamiento y entrega de notificaciones, sin involucrarse en la lógica de mensajes, usuarios o contactos más allá de lo necesario para notificar.

## Dependencias clave
- RabbitMQ: Para la mensajería y distribución de eventos.
- MySQL: Para el almacenamiento de datos relacionados con notificaciones y usuarios.
- Redis: Para la gestión de presencia de usuarios y optimización de acceso a datos.
- Otros servicios de la aplicación (por ejemplo, servicio de usuarios, servicio de mensajes) para recibir eventos que disparan notificaciones.

## Participación en Flujos Core

Este servicio participa activamente en funcionalidades clave de otros servicios dentro del ecosistema de la aplicación de mensajería. Recibe eventos generados por servicios como el de usuarios y el de mensajes, y actúa en consecuencia para notificar a los usuarios sobre acciones relevantes, como:

- Notificación de mensajes enviados y leídos.
- Actualización de estado de contactos (alta, baja, presencia en línea).
- Sincronización de eventos de usuario (creación, cambios de estado).

De esta manera, el servicio de notificaciones asegura la integración y coherencia de la experiencia de usuario en tiempo real, colaborando con los flujos core de la plataforma.

## Funcionalidades Principales Detalladas

### Gestión de Contactos

#### Adición de Contactos
Cuando un usuario agrega a otro como contacto, se siguen estos pasos:
1. Se crea una relación bidireccional entre ambos usuarios en la base de datos
2. Se configuran las suscripciones de WebSocket para que ambos usuarios puedan ver el estado de presencia del otro
3. Si alguno de los usuarios está online, se le notifica inmediatamente de la nueva conexión

Esta funcionalidad puede ser activada tanto mediante WebSocket como mediante eventos de RabbitMQ.

#### Eliminación de Contactos
Cuando un usuario elimina uno o varios contactos:
1. Se borran las relaciones bidireccionales en la base de datos
2. Se limpian las suscripciones de presencia en Redis
3. Se notifica al usuario sobre el resultado de la operación

### Notificaciones en Tiempo Real

#### Recepción de Mensajes
Cuando un usuario recibe un nuevo mensaje:
1. El servicio de chat publica un evento "message.sent" en RabbitMQ
2. El NotificationConsumer recibe este evento
3. Se crea una notificación en formato JSON con detalles del mensaje
4. La notificación se envía al canal WebSocket específico del receptor (/topic/notifications/{userId})

#### Lectura de Mensajes
Cuando un usuario marca mensajes como leídos:
1. El servicio de chat publica un evento "message.read" en RabbitMQ
2. El NotificationConsumer recibe este evento
3. Se crea una notificación especial indicando que los mensajes han sido leídos
4. Esta notificación se envía a través de un canal WebSocket diferente (/topic/seen/{userId}) para actualizar la interfaz en tiempo real

### Gestión de Presencia de Usuarios

#### Autenticación WebSocket
El servicio utiliza STOMP sobre WebSocket para la comunicación en tiempo real. El flujo de autenticación funciona así:

1. **Conexión WebSocket**: El cliente establece conexión TCP y envía frame STOMP CONNECT
2. **Frame CONNECT**: Debe incluir el header `userId` con el ID del usuario
3. **ChannelInterceptor**: `WebSocketConfig` intercepta el CONNECT, extrae el userId y crea un Principal
4. **Registro de sesión**: `WebSocketEventListener` obtiene el userId del Principal y registra la sesión en Redis

```
CONNECT
accept-version:1.1,1.0
userId:12345
passcode:Bearer eyJhbGci...
heart-beat:25000,25000
```

#### Control de Sesiones con Redis
El sistema mantiene un registro de las sesiones activas de cada usuario en Redis:

**Claves creadas al conectar:**
- `session:{sessionId}:user` → userId (mapeo sesión→usuario)
- `session:{sessionId}:username` → username (guardado para limpieza)
- `session:{sessionId}:subscriptions` → [contactIds]
- `user:{userId}:sessions` → [sessionIds] (lista de sesiones del usuario)
- `user:name:{username}` → userId (mapeo username→userId para chat-service)

**Limpieza de Redis:**
- **Desconexión normal**: Cliente envía frame DISCONNECT → Se limpian todas las claves
- **Desconexión abrupta**: SessionDisconnectEvent se dispara cuando TCP cierra → Se limpian todas las claves
- El sistema limpia todas las claves de sesión, incluyendo `user:name:{username}` cuando el usuario no tiene más sesiones activas

**Detección de desconexión:**
- El servidor usa heartbeats STOMP (25s) para detectar si el cliente sigue conectado
- Si el cliente no responde, el servidor cierra la conexión con código de error
- El evento SessionDisconnectEvent se dispara en cualquier caso (normal o anormal)

#### Sincronización de Estado en Tiempo Real
Mediante el uso de STOMP sobre WebSocket, se mantienen sincronizados los estados de presencia entre contactos conectados, permitiendo mostrar actualizaciones instantáneas cuando alguien se conecta o desconecta.
