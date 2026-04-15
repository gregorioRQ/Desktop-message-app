# Flujo: Agregar Contacto

## Resumen

Este documento describe el proceso completo mediante el cual un usuario agrega a otro como contacto en el sistema de mensajería. El flujo involucra la capa de presentación (JavaFX), el cliente WebSocket, el servicio de conexiones, el servicio de notificaciones y las bases de datos local y remota.

El sistema implementa **privacidad de presencia**: solo los contactos confirmados mutualmente podrán ver cuando el otro usuario está online/offline.

## Precondiciones

- El usuario debe estar autenticado en la aplicación
- El contacto a agregar debe existir previamente en el sistema (estar registrado)
- El usuario no puede agregarse a sí mismo como contacto

## Arquitectura General

### Servicios Involucrados

| Servicio | Puerto | Responsabilidad |
|----------|--------|-----------------|
| connection-service | 8083 | Procesa solicitudes de confirmación, notifica al remitente |
| chat-service | 8085 | Gestiona identidades de contacto pendientes |
| profile-service | 8088 | Gestión de usuarios y autenticación |

### Colas de RabbitMQ

El sistema utiliza el exchange `message.exchange` con las siguientes colas:

| Cola | Routing Key | Propósito | Productor | Consumidor |
|------|-------------|-----------|-----------|------------|
| `message.sent.{instanceId}` | `{instanceId}` | Confirmaciones para usuarios online en instancia específica | connection-service | chat-service |
| `message.offline` | `offline` | Confirmaciones para usuarios offline | connection-service | chat-service |

### Tablas de Base de Datos

#### Cliente (SQLite)

| Tabla | Propósito |
|-------|-----------|
| `contacts` | Lista local de contactos con estado `is_confirmed` |

#### Servidor (MySQL)

| Tabla | Propósito |
|-------|-----------|
| `pending_contact_identities` | Identidades de contacto pendientes de entregar |

### Canales de Comunicación

- **WebSocket**: Comunicación bidireccional entre cliente y connection-service
- **RabbitMQ**: Mensajería asíncrona entre servicios
- **REST API**: Recuperación de notificaciones pendientes

## Concepto: Contacto Fantasma

Un "contacto fantasma" es un registro de contacto no confirmado que se crea automáticamente cuando:

1. Un usuario envía un mensaje a otro que no tiene en su lista de contactos
2. El receptor recibe un mensaje de un remitente desconocido

**Características:**
- Se crea con `is_confirmed = false`
- Utiliza un ID temporal hasta que se confirma
- Una vez confirmado, se actualiza con el ID oficial del contacto

## Flujo Principal

### Paso 1: Envío del Primer Mensaje (Contacto Fantasma)

Cuando un remitente envía un mensaje a un usuario que no tiene en su lista de contactos:

**Componente:** `ChatService` (cliente)
**Ubicación:** `websocket-client/src/main/java/com/pola/service/ChatService.java`

El remitente:
1. Genera un ID de contacto temporal/falso para el receptor
2. Crea un registro de contacto en estado no confirmado (`is_confirmed = 0`)
3. Almacena el mensaje y establece el ID temporal como receptor
4. Envía el mensaje al servicio de conexiones

```java
// El remitente usa un ID temporal para el receptor
String tempContactId = "temp_" + UUID.randomUUID().toString();
```

### Paso 2: Recepción del Mensaje en Servicio de Conexiones

**Componente:** `WebSocketService` (connection-service)
**Ubicación:** `connection-service/src/main/java/com/basic_chat/connection_service/service/WebSocketService.java`

El servicio de conexiones recibe el mensaje del remitente y:
1. Valida que el destinatario existe en el sistema
2. Identifica que el remitente no está en la lista de contactos del destinatario
3. Genera una notificación para el cliente receptor

### Paso 3: Creación de Contacto Fantasma (Cliente Receptor)

Cuando el destinatario recibe un mensaje de un remitente desconocido:

**Componente:** `ChatService` + `ContactService` (cliente)

El cliente receptor:
1. Recibe el mensaje a través de WebSocket
2. Detecta que el remitente no existe en su lista de contactos
3. Crea automáticamente un "contacto fantasma" en la base de datos SQLite local
4. Asigna un ID temporal al contacto (correspondiente al ID temporal del remitente)
5. Muestra el mensaje en la interfaz de usuario

**Tabla:** `contacts` (SQLite)
```sql
CREATE TABLE contacts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id TEXT NOT NULL,
    contact_username TEXT NOT NULL,
    contact_user_id TEXT,
    is_blocked INTEGER DEFAULT 0,
    is_confirmed INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, contact_username)
)
```

### Paso 4: Visualización en UI (JavaFX)

**Componente:** `ChatController` + `ContactListController`
**Ubicación:** `websocket-client/src/main/java/com/pola/controller/`

El usuario receptor ve:
- El nuevo contacto aparece en la lista con indicativo visual (ej: ícono de "pendiente")
- Los mensajes se muestran pero se indica que el contacto aún no está confirmado
- Un botón o opción para "Confirmar contacto"

**Nota importante:** En este punto, NO se registra la relación en Notification-Service. El sistema de notificaciones de presencia solo registra contactos **mutuamente confirmados** (ver Paso 7).

### Paso 5: Confirmación del Contacto por el Receptor

El usuario receptor decide aceptar la solicitud de contacto:

**Componente:** `ContactService` (acción desde UI)

El cliente receptor:
1. Marca el contacto como confirmado en la base de datos local
2. Envía una solicitud de confirmación al servicio de conexiones con su ID oficial

```java
// Mensaje enviado al servicio de conexiones
{
    "type": "CONTACT_IDENTITY",
    "from": "user_id_receptor_oficial", 
    "to": "user_id_remitente_temporal",
    "message": {
        "contactId": "id_oficial_del_receptor"
    }
}
```

### Paso 6: Procesamiento en Servicio de Conexiones

**Componente:** `ConnectionMessageDispatcher` + `ContactIdentityHandler`
**Ubicación:** `connection-service/src/main/java/com/basic_chat/connection_service/handler/`

El servicio de conexiones:
1. Recibe la solicitud `CONTACT_IDENTITY` vía WebSocket
2. El `ContactIdentityHandler` procesa el mensaje
3. Determina si el remitente original está online
4. **NUEVO:** Publica evento a RabbitMQ para notificar al Notification-Service

```java
// Evento publicado a RabbitMQ
{
    "type": "CONTACT_IDENTITY_CONFIRM",
    "userId": "user_id_receptor_oficial",
    "username": "username_del_receptor",
    "contactUsername": "username_del_remitente_original",
    "timestamp": 1715432100000
}
```

**Routing Key:** `contact.confirm`
**Exchange:** `contact.exchange`
**Cola:** `contact.events`

#### Caso A: Remitente Online

Si el remitente está conectado al mismo instance de connection-service:
- Se envía el mensaje de inmediato a través de WebSocket
- El remitente actualiza su contacto con el ID oficial

#### Caso B: Remitente Offline u Online en Otra Instancia

Si el remitente no está conectado a este instance:
- Se encola el mensaje en RabbitMQ con routing key específica
- El remitente recibirá el mensaje cuando se conecte (independientemente del instance)

### Paso 7: Recepción en Servicio de Mensajería (Chat-Service)

**Componente:** `ChatMessageRouter` (chat-service)
**Ubicación:** `chat-service/src/main/java/com/basic_chat/chat_service/service/`

Cuando el remitente no está online:
1. El mensaje llega al chat-service vía RabbitMQ
2. Se almacena como registro pending en la tabla `pending_contact_identities`

**Tabla:** `pending_contact_identities` (MySQL)
```sql
CREATE TABLE pending_contact_identities (
    id BIGINT PRIMARY KEY AUTOINCREMENT,
    sender_user_id VARCHAR(255) NOT NULL,
    receiver_temp_id VARCHAR(255) NOT NULL,
    receiver_official_id VARCHAR(255) NOT NULL,
    status VARCHAR(50) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

### Paso 7.5: Procesamiento en Notification-Service (Solo Contactos Mutuos)

**Componente:** `ContactIdentityConsumer` (notification-service)
**Ubicación:** `notification-service/.../consumer/ContactIdentityConsumer.java`

**IMPORTANTE:** Este paso SOLO se ejecuta cuando ambos usuarios se han confirmado mutuamente como contactos. El sistema verifica si el contacto ya existe en la tabla `user_contacts` antes de crear un nuevo registro.

**Condición para registrar en NS:**
- El usuario receptor (B) ya tiene registrado al usuario remitente (A) en su `user_contacts` con `is_confirmed = true`
- Es decir: B ya confirmó a A, y ahora A confirma a B → **Registro mutuo completado**

Flujo:
1. NS recibe el evento de `CONTACT_IDENTITY_CONFIRM` desde RabbitMQ
2. NS busca en `user_contacts` si el contacto ya existe para el usuario
3. **Si ya existe (confirmación mutua):** Actualiza el registro existente
   - `is_confirmed = true`
   - `contact_id = userId_oficial`
4. **Si no existe:** No crea el registro (la relación no es mutua todavía)

**Tabla:** `user_contacts` (MySQL - notification_db)
```sql
CREATE TABLE user_contacts (
    id BIGINT PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(255) NOT NULL,
    contact_id VARCHAR(255) NOT NULL,
    contact_username VARCHAR(255) NOT NULL,
    is_confirmed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, contact_id)
)
```

**Propósito de esta tabla:**
- Solo almacena contactos **mutuamente confirmados**
- Se usa para enviar notificaciones de presencia (online/offline)
- Solo usuarios con `is_confirmed = true` ven cuando el contacto está online

### Paso 8: Entrega al Remitente (Recuperación)

Cuando el remitente se conecta:

**Componente:** `ChatConnectionService` (chat-service)
1. El servicio consulta los pending_contact_identities pendientes
2. Envía los mensajes `CONTACT_IDENTITY` al remitente
3. Actualiza el estado a `DELIVERED`

### Paso 9: Actualización del Contacto (Cliente Remitente)

**Componente:** `ContactService` + `ContactIdentityHandler` (cliente)
**Ubicación:** `websocket-client/src/main/java/com/pola/handler/ContactIdentityHandler.java`

El cliente remitente:
1. Recibe el mensaje `CONTACT_IDENTITY`
2. Actualiza el contacto existente (con ID temporal) con el ID oficial recibido
3. Persiste el cambio en SQLite
4. Actualiza la UI para eliminar el indicativo de "pendiente"

## Componentes Participantes

| Componente | Ubicación | Tecnología | Responsabilidad |
|------------|-----------|------------|-----------------|
| ChatService | websocket-client/.../service/ | JavaFX | Coordinar envío y recepción de mensajes |
| ContactService | websocket-client/.../service/ | Java | Gestionar contactos locales (crear, confirmar, actualizar) |
| ContactIdentityHandler | websocket-client/.../handler/ | Java | Procesar confirmaciones de contacto recibidas |
| ContactRepository | websocket-client/.../repository/ | SQLite | Persistencia local de contactos |
| WebSocketService | connection-service/.../service/ | Spring WebSocket | Rutear mensajes en tiempo real |
| ConnectionMessageDispatcher | connection-service/.../dispatcher/ | Java | Despachar mensajes a handlers apropiados |
| ContactIdentityHandler | connection-service/.../handler/ | Java | Procesar solicitudes de confirmación en el servidor |
| RabbitMQProducerService | connection-service/.../service/ | RabbitMQ | Publicar eventos de confirmación a NS |
| ChatMessageRouter | chat-service/.../service/ | Spring | Encolar mensajes para usuarios offline |
| PendingContactIdentityRepository | chat-service/.../repository/ | Spring Data JPA | Persistencia en MySQL para identities pendientes |
| ContactIdentityConsumer | notification-service/.../consumer/ | RabbitMQ Listener | Procesar confirmaciones de contacto en NS |
| UserContactRepository | notification-service/.../repository/ | Spring Data JPA | Gestionar contactos en NS |

## Estructura de Clases Principales

### connection-service

| Clase | Responsabilidad |
|-------|-----------------|
| `ConnectionMessageDispatcher` | Dispatcher que delega mensajes a handlers específicos |
| `ContactIdentityHandler` | Procesa solicitudes de confirmación de contacto |
| `MessageRouterService` | Enruta mensajes según estado de conexión |
| `SessionRegistryService` | Gestiona sesiones WebSocket y consulta Redis |
| `RabbitMQProducerService` | Publica mensajes a RabbitMQ |

### chat-service

| Clase | Responsabilidad |
|-------|-----------------|
| `ContactIdentityMessageHandler` | Procesa confirmaciones para usuarios offline |
| `PendingContactIdentityRepository` | Acceso a tabla pending_contact_identities |

### websocket-client

| Clase | Responsabilidad |
|-------|-----------------|
| `ContactService` | Gestiona contactos locales en SQLite |
| `ContactIdentityHandler` | Procesa confirmaciones recibidas del servidor |
| `ContactRepository` | Acceso a tabla contacts |
| `ChatService` | Maneja envío y recepción de mensajes |

## Almacenamiento

### Cliente (SQLite)
- **Tabla:** `contacts`
- **Propósito:** Almacenar lista local de contactos del usuario
- **Contiene:** username del contacto, ID temporal (contact_user_id), ID oficial (cuando se actualiza), estado de confirmación, estado de bloqueo

### Servidor (MySQL) - Chat-Service
- **Tabla:** `pending_contact_identities`
- **Propósito:** Guardar identities de contacto pendientes para usuarios offline
- **Contiene:** sender_user_id, receiver_temp_id, receiver_official_id, status, created_at

### Servidor (MySQL) - Notification-Service
- **Tabla:** `user_contacts`
- **Propósito:** Almacenar relaciones de contactos mutuamente confirmados para notificaciones de presencia
- **Contiene:** user_id, contact_id, contact_username, is_confirmed, created_at
- **Nota:** Solo se registra cuando ambos usuarios se han confirmado mutuamente. Solo los contactos con `is_confirmed = true` reciben notificaciones de presencia online/offline.

- **Tabla:** `user_presence` (pendiente de implementación)
- **Propósito:** Almacenar el estado online/offline de cada usuario
- **Contiene:** user_id, status (ONLINE/OFFLINE), last_seen

## Flujo de Mensajes

### Mensaje: Primer Mensaje (Remitente → Receptor)
```protobuf
message ChatMessage {
    string from = 1;
    string to = 2;           // ID temporal del receptor
    string content = 3;
    string message_id = 4;
}
```

### Mensaje: Confirmación de Contacto (Receptor → Remitente)
```protobuf
message ContactIdentity {
    string type = 1;         // "CONTACT_IDENTITY"
    string from = 2;         // ID oficial del receptor
    string to = 3;           // ID temporal del remitente
    ContactIdentityMessage message = 4;
    
    message ContactIdentityMessage {
        string contactId = 1; // ID oficial del receptor
    }
}
```

## Diagrama del Flujo Completo

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ ENVÍO DE PRIMER MENSAJE                                                     │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente A]                        [Servidor]                      [Cliente B]
   │                                    │                               │
   │── Genera ID temporal ─────────────│                               │
   │── Crea contacto fantasma ───────│                               │
   │                                    │                               │
   │── ChatMessage ──────────────────>│                               │
   │   (recipient = temp_UUID)        │                               │
   │                                    │── Valida destinatario         │
   │                                    │── Determina estado de B       │
   │                                    │                               │
   │                                    │── B online? ──┐               │
   │                                    │               │               │
   │                                    │               ▼               │
   │                                    │           [Si online]         │
   │                                    │           WebSocket ─────────>│
   │                                    │                               │── Crea contacto
   │                                    │                               │   fantasma para A
   │                                    │                               │── Muestra mensaje
   │                                    │                               │
   │                                    │                               │
   │                                    │<──────── [Si offline]          │
   │                                    │── Cola offline ─────────────>│
   │                                    │                               │   (al reconectarse)


┌─────────────────────────────────────────────────────────────────────────────┐
│ CONFIRMACIÓN DE CONTACTO                                                    │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente B]                        [Servidor]                      [Cliente A]
   │                                    │                               │
   │── Usuario B presiona ──────────────│                               │
   │   "Confirmar contacto"             │                               │
   │                                    │                               │
   │── ContactIdentity ───────────────>│                               │
   │   from: userId_B_oficial           │                               │
   │   to: temp_UUID_A                  │                               │
   │   contactId: userId_B_oficial      │                               │
   │                                    │── ContactIdentityHandler        │
   │                                    │── Busca A por temp_ID           │
   │                                    │                               │
   │── Response ────────────────────────│                               │
   │                                    │── A online? ──┐               │
   │                                    │               │               │
   │                                    │               ▼               │
   │                                    │           [Si online]         │
   │                                    │           WebSocket ─────────>│
   │                                    │                               │── ContactIdentityHandler
   │                                    │                               │── Busca contacto temp
   │                                    │                               │── Actualiza con ID oficial
   │                                    │                               │── is_confirmed = true
   │                                    │                               │── Refresca UI
   │                                    │                               │
   │                                    │                               │
   │                                    │<──────── [Si offline]          │
   │                                    │── pending_contact ──────────>│
   │                                    │   identities                    │   (al reconectarse)


┌─────────────────────────────────────────────────────────────────────────────┐
│ CASO ESPECIAL: BLOQUEO ANTES DE CONFIRMAR                                   │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente B]                        [Servidor]                      [Cliente A]
   │                                    │                               │
   │── Usuario B presiona ──────────────│                               │
   │   "Bloquear"                       │                               │
   │                                    │                               │
   │── BlockContactRequest ───────────>│                               │
   │                                    │── Registra en contact_blocks  │
   │                                    │── Contacto fantasma:            │
   │                                    │   is_blocked = true             │
   │                                    │                               │
   │── BlockContactResponse ────────────│                               │
   │                                    │                               │
   │── NO se envía ContactIdentity ─────│                               │
│   al remitente A                     │                               │
   │                                    │                               │
   │                                    │                               │   A nunca recibe
   │                                    │                               │   confirmación
```

## Notas Adicionales

- El flujo es completamente asíncrono mediante WebSocket y RabbitMQ
- El servicio de conexiones (puerto 8083) es el responsable de gestionar las relaciones de contactos en tiempo real
- El servicio de mensajería (puerto 8085) maneja el almacenamiento y entrega de identidades pendientes
- El servicio de notificaciones (puerto 8084) solo registra contactos **mutuamente confirmados** en la tabla `user_contacts`
- Solo los contactos registrados en NS con `is_confirmed = true` reciben notificaciones de presencia online/offline
- El sistema soporta múltiples instancias de connection-service mediante routing en RabbitMQ
- Los IDs temporales garantizan que los mensajes se puedan enviar antes de la confirmación oficial
- La base de datos SQLite local almacena el estado de sin confirmar hasta que se recibe el ID oficial

## Casos Especiales

### Bloqueo de Contacto No Confirmado
Si el receptor bloquea el contacto antes de confirmarlo, el contacto fantasma se marca como bloqueado y no se envía la confirmación al remitente.

### Múltiples Mensajes Antes de Confirmación
Todos los mensajes usan el ID temporal. Una vez confirmado, el remitente actualiza su historial de mensajes para referenciar el ID oficial.

### Expiración de Contactos Fantasma
Los contactos no confirmados pueden ser limpiados periódicamente si no se confirman dentro de un período de tiempo determinado (configurable).
