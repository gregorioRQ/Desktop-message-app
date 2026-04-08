# Flujo de Confirmación de Contacto

Este documento describe el flujo completo mediante el cual un usuario confirma a otro como contacto en el sistema de mensajería. El sistema utiliza "contactos fantasma" que se confirman cuando el receptor acepta la solicitud de contacto.

---

## 1. Arquitectura General

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

---

## 2. Concepto: Contacto Fantasma

Un "contacto fantasma" es un registro de contacto no confirmado que se crea automáticamente cuando:

1. Un usuario envía un mensaje a otro que no tiene en su lista de contactos
2. El receptor recibe un mensaje de un remitente desconocido

**Características:**
- Se crea con `is_confirmed = false`
- Utiliza un ID temporal hasta que se confirma
- Una vez confirmado, se actualiza con el ID oficial del contacto

---

## 3. Flujo Detallado Paso a Paso

### Paso 1: Envío del Primer Mensaje

El usuario A envía un mensaje al usuario B que no tiene en su lista de contactos.

**Qué ocurre:**
- El cliente genera un ID temporal para B: `temp_{uuid}`
- Crea un registro de contacto en SQLite con `is_confirmed = false`
- Envía el mensaje al connection-service

**Datos del mensaje:**
```
ChatMessage:
  - from: username de A
  - to: temp_{uuid} (ID temporal de B)
  - content: contenido del mensaje
  - messageId: ID único del mensaje
```

---

### Paso 2: Llega a connection-service

El mensaje llega al `ChatMessageHandler` via `ConnectionMessageDispatcher`.

**Qué ocurre:**
- Se valida que el destinatario existe en el sistema
- Se determina el estado de conexión del destinatario
- Se enruta el mensaje según corresponda

---

### Paso 3: Mensaje llega al receptor B

**Caso A: B está online**

- El mensaje se entrega inmediatamente por WebSocket
- B recibe el mensaje y detecta que A no está en su lista
- El cliente B crea un "contacto fantasma" para A

**Caso B: B está offline**

- El mensaje se guarda en chat-service (cola offline)
- Se entrega cuando B se conecte

---

### Paso 4: Receptor B confirma el contacto

B decide aceptar la solicitud de contacto y presiona "Confirmar".

**Qué ocurre:**
1. El cliente B envía `ContactIdentity` por WebSocket
2. El mensaje contiene el ID oficial de B
3. El cliente actualiza su SQLite: `is_confirmed = true`

**Datos del mensaje:**
```
ContactIdentity:
  - from: ID oficial de B
  - to: temp_{uuid} de A (referencia al remitente)
  - contactId: ID oficial de B
```

---

### Paso 5: connection-service procesa ContactIdentity

El `ConnectionMessageDispatcher` recibe el mensaje y delega a `ContactIdentityHandler`.

**Qué ocurre:**
1. Se extrae el ID oficial del receptor
2. Se busca al remitente original (A) por su ID temporal
3. Se determina el estado de conexión de A
4. Se enruta la confirmación

---

### Paso 6: Notificación al remitente A

#### Caso A: A está online en la misma instancia

- La confirmación se envía directamente por WebSocket
- A recibe `ContactIdentity` con el ID oficial de B

#### Caso B: A está online en otra instancia

- Se publica en cola `message.sent.{instanceId}`
- La instancia correspondiente reenvía a A por WebSocket

#### Caso C: A está offline

- Se guarda en `pending_contact_identities` en chat-service
- Se entrega cuando A se conecte

---

### Paso 7: Cliente A recibe la confirmación

El `ContactIdentityHandler` del cliente procesa el mensaje.

**Qué ocurre:**
1. Extrae el ID oficial de B del mensaje
2. Busca el contacto existente (con ID temporal)
3. Actualiza el contacto con el ID oficial
4. Actualiza `is_confirmed = true`
5. Refresca la UI

---

## 4. Persistencia en Base de Datos

### Tabla: contacts (SQLite - Cliente)

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

**Estados:**
- `is_confirmed = 0`: Contacto fantasma (pendiente de confirmación)
- `is_confirmed = 1`: Contacto confirmado

### Tabla: pending_contact_identities (MySQL - Servidor)

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

---

## 5. Casos Especiales

### Caso: Receptor bloquea antes de confirmar

Si el usuario B bloquea al usuario A antes de confirmar el contacto:

- El contacto fantasma se marca como bloqueado (`is_blocked = true`)
- No se envía la confirmación al remitente A
- El mensaje de bloqueo se procesa según el flujo de bloqueo

### Caso: Múltiples mensajes antes de confirmación

Todos los mensajes usan el ID temporal hasta que se confirma.

Cuando llega la confirmación:
1. El contacto existente se actualiza con el ID oficial
2. Todos los mensajes pendientes se asocian al ID oficial
3. El historial se actualiza automáticamente

### Caso: Remitente envía mensaje a contacto ya confirmado

Si A envía un mensaje a B y B ya tiene a A confirmado:
- El mensaje se entrega normalmente
- No se crea ningún contacto nuevo

---

## 6. Formato de Mensajes

### ContactIdentity (Cliente → Servidor)

```json
{
  "contactIdentityMessage": {
    "from": "userId_oficial_B",
    "to": "temp_userId_A",
    "contactId": "userId_oficial_B"
  }
}
```

### ContactIdentity (Servidor → Cliente)

```json
{
  "contactIdentityMessage": {
    "from": "userId_oficial_B",
    "to": "temp_userId_A",
    "contactId": "userId_oficial_B"
  }
}
```

### ChatMessage con ID Temporal

```json
{
  "chatMessage": {
    "messageId": "msg-123",
    "sender": "usuarioA",
    "recipient": "temp_uuid-456",
    "content": "Hola B!"
  }
}
```

---

## 7. Estructura de Clases Principales

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

---

## 8. Diagrama del Flujo Completo

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ENVÍO DE PRIMER MENSAJE                                   │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente A]                         [Servidor]                    [Cliente B]
     │                                  │                              │
     │── Genera ID temporal ─────────────│                              │
     │── Crea contacto fantasma ───────│                              │
     │                                  │                              │
     │── ChatMessage ──────────────────>│                              │
     │   (recipient = temp_UUID)        │                              │
     │                                  │── Valida destinatario        │
     │                                  │── Determina estado de B      │
     │                                  │                              │
     │                                  │── B online? ──┐              │
     │                                  │              │              │
     │                                  │              ▼              │
     │                                  │         [Si online]          │
     │                                  │         WebSocket ─────────>│
     │                                  │                              │── Crea contacto
     │                                  │                              │   fantasma para A
     │                                  │                              │── Muestra mensaje
     │                                  │                              │
     │                                  │              │              │
     │                                  │<──────── [Si offline]        │
     │                                  │── Cola offline ─────────────>│
     │                                  │                              │ (al reconectarse)


┌─────────────────────────────────────────────────────────────────────────────┐
│                    CONFIRMACIÓN DE CONTACTO                                  │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente B]                         [Servidor]                    [Cliente A]
     │                                  │                              │
     │── Usuario B presiona ────────────│                              │
     │   "Confirmar contacto"          │                              │
     │                                  │                              │
     │── ContactIdentity ─────────────>│                              │
     │   from: userId_B_oficial        │                              │
     │   to: temp_UUID_A               │                              │
     │   contactId: userId_B_oficial   │                              │
     │                                  │── ContactIdentityHandler     │
     │                                  │── Busca A por temp_ID       │
     │                                  │                              │
     │── Response ─────────────────────│                              │
     │                                  │── A online? ──┐              │
     │                                  │              │              │
     │                                  │              ▼              │
     │                                  │         [Si online]          │
     │                                  │         WebSocket ─────────>│
     │                                  │                              │── ContactIdentityHandler
     │                                  │                              │── Busca contacto temp
     │                                  │                              │── Actualiza con ID oficial
     │                                  │                              │── is_confirmed = true
     │                                  │                              │── Refresca UI
     │                                  │                              │
     │                                  │              │              │
     │                                  │<──────── [Si offline]        │
     │                                  │── pending_contact ────────>│
     │                                  │   identities               │ (al reconectarse)


┌─────────────────────────────────────────────────────────────────────────────┐
│                    CASO ESPECIAL: BLOQUEO ANTES DE CONFIRMAR                │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente B]                         [Servidor]                    [Cliente A]
     │                                  │                              │
     │── Usuario B presiona ───────────│                              │
     │   "Bloquear"                    │                              │
     │                                  │                              │
     │── BlockContactRequest ─────────>│                              │
     │                                  │── Registra en contact_blocks│
     │                                  │── Contacto fantasma:        │
     │                                  │   is_blocked = true         │
     │                                  │                              │
     │── BlockContactResponse ────────│                              │
     │                                  │                              │
     │── NO se envía ContactIdentity ─│                              │
     │   al remitente A               │                              │
     │                                  │                              │
     │                                  │                              │ A nunca recibe
     │                                  │                              │ confirmación
```

---

## 9. Resumen

El flujo de confirmación de contacto garantiza que:

1. **Contactos fantasma**: Los usuarios pueden enviar mensajes antes de la confirmación
2. **Confirmación bidireccional**: El receptor confirma con su ID oficial
3. **Actualización automática**: El remitente recibe el ID oficial y actualiza
4. **Entrega offline**: Las confirmaciones se guardan si el destinatario está offline
5. **Integración con bloqueo**: Si el receptor bloquea antes de confirmar, no se envía la confirmación
