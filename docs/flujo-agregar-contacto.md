# Flujo: Agregar Contacto

## Resumen

Este documento describe el flujo mediante el cual un usuario agrega a otro como contacto en el sistema de mensajería. El flujo es **unidireccional**: cada usuario debe agregar explícitamente a sus contactos.

## Precondiciones

- El usuario debe estar autenticado en la aplicación
- El contacto a agregar debe existir previamente en el sistema (estar registrado)
- El usuario no puede agregarse a sí mismo como contacto

## Arquitectura General

### Servicios Involucrados

| Servicio | Puerto | Responsabilidad |
|----------|--------|-----------------|
| connection-service | 8083 | Recibe solicitud AddContact, publica evento a RabbitMQ |
| notification-service | 8084 | Consume evento y crea registros en contact_users |

### Colas de RabbitMQ

| Cola | Routing Key | Propósito | Productor | Consumidor |
|------|-------------|-----------|-----------|------------|
| `contact.events` | `contact.confirm` | Eventos de agregar contacto | connection-service | notification-service |

### Tablas de Base de Datos

#### Cliente (SQLite)

| Tabla | Propósito |
|-------|-----------|
| `contacts` | Lista local de contactos |

#### Servidor (MySQL) - Notification-Service

| Tabla | Propósito |
|-------|-----------|
| `contact_users` | Relaciones de contactos (bidireccionales) |
| `users` | Usuarios registrados con estado online/offline |

**Estructura contact_users:**
```sql
CREATE TABLE contact_users (
    username VARCHAR(255) NOT NULL, -- Username del usuario propietario
    contact_username VARCHAR(255) NOT NULL, -- Username del contacto
    PRIMARY KEY (username, contact_username)
)
```

**Nota:** La columna `username` almacena el **username** del usuario propietario (no el UUID).

## Flujo Principal

### Paso 1: Recepción de Mensaje de Usuario Desconocido

Cuando el receptor (Usuario B) recibe un mensaje de un remitente (Usuario A) que no tiene en su lista de contactos:

**Componente:** `ChatService` + `ContactService` (cliente)

El cliente receptor:
1. Recibe el mensaje a través de WebSocket
2. Detecta que el remitente no existe en su lista de contactos local (SQLite)
3. Muestra el mensaje en la UI con opción para "Agregar contacto"

### Paso 2: Usuario Presiona "Agregar Contacto"

El usuario B decide agregar a A como contacto:

**Componente:** `ContactService` (acción desde UI)

El cliente:
1. Crea el contacto localmente en SQLite
2. Envía solicitud `AddContactRequest` al servidor por WebSocket

**Protobuf:**
```protobuf
message AddContactRequest {
    string sender = 1;           // Username del usuario que agrega (B)
    string contact_username = 2;  // Username del contacto a agregar (A)
}
```

### Paso 3: Procesamiento en Connection-Service

**Componente:** `AddContactHandler` (connection-service)
**Ubicación:** `connection-service/src/main/java/com/basic_chat/connection_service/handler/AddContactHandler.java`

El servicio de conexiones:
1. Recibe el mensaje protobuf `AddContactRequest`
2. Extrae sender (username B) y contact_username (username A)
3. Publica evento JSON a RabbitMQ en la cola `contact.events`

**Mensaje RabbitMQ:**
```json
{
    "sender": "username_B",
    "contact_username": "username_A"
}
```

### Paso 4: Procesamiento en Notification-Service

**Componente:** `AddContactConsumer` (notification-service)
**Ubicación:** `notification-service/src/main/java/com/basic_chat/notifiation_service/consumer/AddContactConsumer.java`

El notification-service:
1. Consume el mensaje de la cola `contact.events`
2. Crea **UN registro** en la tabla `contact_users`:
   - Registro: `username="B", contact_username="A"`
3. La relación es unidireccional - el usuario A debe agregar a B explícitamente cuando lo desee

**Flujo - NO hay:**
- Creación automática del contacto inverso
- Confirmación mutua requerida
- IDs temporales
- Contactos "fantasma"
- Estados de confirmación pendiente

## Componentes Participantes

| Componente | Ubicación | Responsabilidad |
|------------|-----------|-----------------|
| `ChatService` | websocket-client | Coordinar envío/recepción de mensajes |
| `ContactService` | websocket-client | Gestionar contactos locales en SQLite |
| `AddContactHandler` | connection-service | Recibir solicitud AddContact y publicar a RabbitMQ |
| `RabbitMQProducerService` | connection-service | Publicar eventos a RabbitMQ |
| `AddContactConsumer` | notification-service | Consumir eventos y crear registros bidireccionales |
| `ContactUserRepository` | notification-service | Acceso a tabla contact_users |

## Flujo de Mensajes

### Diagrama Simplificado

```
[Cliente B]                    [Connection-Service]           [Notification-Service]
    │                                   │                              │
    │── Recibe mensaje de A ───────────│                              │
    │── Muestra "Agregar contacto"     │                              │
    │                                   │                              │
    │── Usuario presiona agregar ─────>│                              │
    │   AddContactRequest              │                              │
    │   sender: B                      │                              │
    │   contact_username: A            │                              │
    │                                   │                              │
    │                                   │── Extrae datos  ─────────────>│
    │                                   │   Crea evento JSON           │
    │                                   │   {"sender":"B",             │
    │                                   │    "contact_username":"A"}   │
    │                                   │                              │
│                 │               │── Crea registro 1:
│                 │               │   username="B",
│                 │               │   contact_username="A"
│                 │               │
│                 │               │── Crea registro 2:
│                 │               │   username="A",
│                 │               │   contact_username="B"
    │                                   │                              │
    │                                   │                              │── Relación lista para
    │                                   │                              │   notificaciones SSE
```

## Notas Adicionales

- El flujo es **unidireccional**: cada usuario debe agregar a sus contactos explícitamente
- El contacto inverso (A→B) se crea cuando A presiona "agregar contacto"
- Las notificaciones de presencia (online/offline) funcionan para ambos lados una vez creada la relación
- La tabla `contact_users` usa **usernames** como claves, no UUIDs

## Casos Especiales

### Contacto ya existe
Si la relación ya existe en `contact_users`, la operación es idempotente (no falla, simplemente no crea duplicados debido a la clave primaria compuesta).

### Usuario no existe
Si el `contact_username` no existe en el sistema, el mensaje se procesa pero no se crean registros (el consumidor valida la existencia del usuario).
