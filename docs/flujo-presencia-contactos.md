# Flujo: Presencia de Contactos

## Resumen

Este documento describe el proceso mediante el cual los usuarios pueden ver el estado online/offline de sus contactos en el sistema de mensajería. El sistema está diseñado para mantener la privacidad: solo los contactos **mutuamente confirmados** pueden ver cuándo el otro usuario está conectado.

El sistema de presencia utiliza una arquitectura simplificada que elimina la necesidad de Redis Pub/Sub, utilizando en su lugar:
- **Base de datos (MySQL)** para almacenar el estado de presencia
- **REST API** para consultas y actualizaciones
- **SSE (Server-Sent Events)** para notificaciones push a clientes interesados

## Precondiciones

- El usuario debe estar autenticado en la aplicación
- El usuario debe tener al menos un contacto **mutuamente confirmado** en el sistema
- El contacto debe haber confirmado al usuario y el usuario debe haber confirmado al contacto

## Arquitectura del Sistema

### Componentes Involucrados

| Componente | Puerto | Responsabilidad |
|------------|--------|-----------------|
| WebSocket Client | N/A | Consultar estado de contactos al iniciar, recibir notificaciones SSE |
| Connection Service | 8083 | Detectar conexión/desconexión de usuarios, notificar a NS |
| Notification Service | 8084 | Gestionar estado en DB, enviar notificaciones a interesados |
| MySQL (NS) | 3306 | Almacenar user_contacts y user_presence |

### Diagrama de Arquitectura

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                           ARQUITECTURA DE PRESENCIA                                           │
└─────────────────────────────────────────────────────────────────────────────────────────────┘

  CLIENTE A                          CS (8083)                    NS (8084)                    DB
       │                                │                             │                          │
       │ 1. Se conecta                  │                             │                          │
       │────────────────────────────────►│                             │                          │
       │                                │                             │                          │
       │                                │ 2. Detecta conexión         │                          │
       │                                │                             │                          │
       │                                │ 3. POST /presence/online    │                          │
       │                                │────────────────────────────►│                          │
       │                                │                             │ 4. UPDATE user_presence   │
       │                                │                             │    SET status=ONLINE    │
       │                                │                             │────────────────────────►│
       │                                │                             │                          │
       │                                │                             │                          │
       │ 5. GET /presence/contacts       │                             │                          │
       │────────────────────────────────►│                             │                          │
       │                                │    GET /presence/contacts   │                          │
       │                                │────────────────────────────►│                          │
       │                                │                             │ 6. SELECT user_contacts  │
       │                                │                             │    + user_presence       │
       │                                │                             │────────────────────────►│
       │                                │◄────────────────────────────│                          │
       │◄───────────────────────────────┤                             │ 7. Devuelve estados     │
       │                                │                             │                          │
       │ 8. Muestra "● Conectado"        │                             │                          │
       │   junto a contactos online      │                             │                          │
       │                                │                             │                          │
       │                                │                             │                          │
       │                                │                             │                          │
       │                     CLIENTE B                              │                          │
       │                     se desconecta                           │                          │
       │                                │                             │                          │
       │                                │ 9. Detecta desconexión      │                          │
       │                                │                             │                          │
       │                                │ 10. POST /presence/offline  │                          │
       │                                │────────────────────────────►│                          │
       │                                │                             │ 11. UPDATE user_presence│
       │                                │                             │    SET status=OFFLINE   │
       │                                │                             │────────────────────────►│
       │                                │                             │                          │
       │                                │                             │ 12. SELECT interesados  │
       │                                │                             │    (user_contacts)      │
       │                                │                             │────────────────────────►│
       │                                │                             │                          │
       │ 13. SSE: event:presence        │                             │                          │
       │   data: {type:OFFLINE,         │                             │                          │
       │           userId:B}            │                             │                          │
       │◄────────────────────────────────│                             │                          │
       │                                │                             │                          │
       │ 14. Actualiza UI:              │                             │                          │
       │   "○ Desconectado"              │                             │                          │
       │                                │                             │                          │
```

## Flujo Principal

### 4.1 Consulta de Estado al Iniciar Sesión

Cuando un usuario se conecta a la aplicación, el cliente consulta automáticamente el estado de sus contactos:

**Componente:** `SseNotificationClient` (cliente)
**Endpoint:** `GET /api/presence/contacts`

```
1. Cliente se conecta a la aplicación
      │
      ▼
2. Cliente envía solicitud HTTP a NS:
   GET http://localhost:8084/api/presence/contacts
   Header: X-User-Id: {userId}
      │
      ▼
3. Notification-Service procesa la solicitud:
   a) Consulta user_contacts:
      SELECT * FROM user_contacts 
      WHERE user_id = '{userId}' AND is_confirmed = true
      │
      b) Para cada contacto, consulta user_presence:
      SELECT * FROM user_presence WHERE contact_id = '{contactId}'
      │
      ▼
4. NS responde con lista de contactos y estados:
   {
     "contacts": [
       {"username": "juan", "userId": "...", "status": "ONLINE"},
       {"username": "maria", "userId": "...", "status": "OFFLINE"}
     ]
   }
      │
      ▼
5. Cliente actualiza la UI:
   - Muestra "●" junto a contactos con status ONLINE
   - Muestra "○" junto a contactos con status OFFLINE
```

### 4.2 Notificación de Cambio de Estado (Usuario se Conecta)

Cuando un usuario se conecta al sistema:

**Componente:** `SessionRegistryService` (connection-service)
**Endpoint:** `POST /api/presence/online`

```
1. Usuario B se conecta a la aplicación
      │
      ▼
2. Connection-Service detecta la conexión:
   SessionRegistryService.registerSession(userId_B, sessionId)
      │
      ▼
3. CS notifica a NS:
   POST http://localhost:8084/api/presence/online
   Body: { "userId": "userId_B", "username": "b" }
      │
      ▼
4. Notification-Service actualiza la base de datos:
   UPDATE user_presence 
   SET status = 'ONLINE', last_seen = NOW() 
   WHERE user_id = 'userId_B'
   
   Si no existe: INSERT INTO user_presence (...)
      │
      ▼
5. NS busca usuarios interesados (que tienen a B como contacto):
   SELECT user_id FROM user_contacts 
   WHERE contact_id = 'userId_B' AND is_confirmed = true
      │
      ▼
6. Para cada usuario interesado:
   - Si tiene conexión SSE activa → envía evento
   - Si no tiene conexión → omite (se actualizará al conectar)
      │
      ▼
7. Cliente A recibe evento SSE:
   event: presence
   data: {"type":"ONLINE","userId":"userId_B","username":"b"}
      │
      ▼
8. Cliente A actualiza UI:
   ContactService.setContactOnline(userId_B, true)
      │
      ▼
9. UI muestra "● Conectado" junto al contacto B
```

### 4.3 Notificación de Cambio de Estado (Usuario se Desconecta)

Cuando un usuario se desconecta del sistema:

**Componente:** `SessionRegistryService` (connection-service)
**Endpoint:** `POST /api/presence/offline`

```
1. Usuario B cierra sesión o pierde conexión
      │
      ▼
2. Connection-Service detecta la desconexión:
   SessionRegistryService.removeSession(sessionId)
      │
      ▼
3. CS notifica a NS:
   POST http://localhost:8084/api/presence/offline
   Body: { "userId": "userId_B" }
      │
      ▼
4. Notification-Service actualiza la base de datos:
   UPDATE user_presence 
   SET status = 'OFFLINE' 
   WHERE user_id = 'userId_B'
      │
      ▼
5. NS busca usuarios interesados:
   SELECT user_id FROM user_contacts 
   WHERE contact_id = 'userId_B' AND is_confirmed = true
      │
      ▼
6. Para cada usuario interesado → envía evento SSE
      │
      ▼
7. Clientes actualizan UI:
   ContactService.setContactOnline(userId_B, false)
      │
      ▼
8. UI muestra "○ Desconectado" junto al contacto
```

## Endpoints API

### GET /api/presence/contacts

Consulta el estado de los contactos confirmados del usuario.

```
GET http://localhost:8084/api/presence/contacts
Header: X-User-Id: {userId}

Response (200):
{
  "contacts": [
    {
      "username": "juan",
      "userId": "uuid-123",
      "status": "ONLINE"
    },
    {
      "username": "maria", 
      "userId": "uuid-456",
      "status": "OFFLINE"
    }
  ]
}
```

### POST /api/presence/online

Notifica que un usuario se ha conectado.

```
POST http://localhost:8084/api/presence/online
Content-Type: application/json

{
  "userId": "uuid-123",
  "username": "juan"
}

Response (200):
{
  "status": "success"
}
```

### POST /api/presence/offline

Notifica que un usuario se ha desconectado.

```
POST http://localhost:8084/api/presence/offline
Content-Type: application/json

{
  "userId": "uuid-123"
}

Response (200):
{
  "status": "success"
}
```

## Base de Datos

### Tabla: user_presence

Almacena el estado online/offline de cada usuario.

```sql
CREATE TABLE user_presence (
    id BIGINT PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**Campos:**
- `user_id`: ID único del usuario
- `status`: ONLINE o OFFLINE
- `last_seen`: Última vez que el usuario estuvo online
- `created_at`: Fecha de creación del registro

### Tabla: user_contacts

Almacena las relaciones de contactos mutuamente confirmados.

```sql
CREATE TABLE user_contacts (
    id BIGINT PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(255) NOT NULL,
    contact_id VARCHAR(255) NOT NULL,
    contact_username VARCHAR(255) NOT NULL,
    is_confirmed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(user_id, contact_id)
);
```

**Campos:**
- `user_id`: Usuario que tiene el contacto
- `contact_id`: ID del contacto
- `contact_username`: Username del contacto
- `is_confirmed`: Indica si la relación es mutua (solo true = recibe notificaciones)
- `created_at`: Fecha de creación

**Nota:** Solo se registra en esta tabla cuando ambos usuarios se han confirmado mutuamente.

## Diferencias con Sistema Anterior

| Aspecto | Sistema Anterior (con Redis Pub/Sub) | Sistema Actual (con DB) |
|---------|---------------------------------------|--------------------------|
| **Pub/Sub** | ✅ Usa Redis Pub/Sub | ❌ Eliminado |
| **Estado** | Almacenado en Redis | Almacenado en DB (MySQL) |
| **Consulta inicial** | No necesaria (push inmediato) | Cliente consulta al iniciar |
| **Notificaciones** | Pub/Sub → Listener → SSE | REST → DB Query → SSE |
| **Complejidad** | Alta (múltiples componentes) | Baja (REST + DB) |
| **Persistencia** | Se pierde al reiniciar | Se mantiene |

## Privacidad

### Principio Fundamental

Solo los contactos **mutuamente confirmados** pueden ver el estado online/offline del otro usuario.

### Implementación

1. **Registro en user_contacts**: Solo se crea cuando ambos usuarios se han confirmado
2. **Consulta de interesados**: `SELECT ... WHERE is_confirmed = true`
3. **Verificación antes de notificar**: El sistema verifica `is_confirmed` antes de enviar eventos SSE

### Tabla de Privacidad

| Estado del Contacto | ¿Ve Estado Online? |
|--------------------|---------------------|
| No es contacto | ❌ No |
| Contacto fantasma (no confirmado) | ❌ No |
| Contacto confirmado pero no mutuo | ❌ No |
| **Contacto mutuamente confirmado** | ✅ **Sí** |

## Casos de Uso

### Caso 1: Usuario se Conecta por Primera Vez

```
1. Usuario A abre la aplicación
2. Cliente consulta GET /api/presence/contacts
3. NS devuelve lista vacía (no hay contactos mutuos aún)
4. Cliente muestra lista de contactos vacía
```

### Caso 2: Usuario con Contactos Mutuos se Conecta

```
1. Usuario A tiene contactos mutuos: B y C
2. Cliente consulta GET /api/presence/contacts
3. NS: B está ONLINE, C está OFFLINE
4. Cliente muestra: B con "●", C con "○"
```

### Caso 3: Contacto se Conecta Mientras Usuario está Activo

```
1. Usuario B se conecta
2. CS notifica a NS: POST /presence/online
3. NS actualiza user_presence
4. NS busca interesados (usuarios que tienen a B como contacto)
5. NS envía SSE a usuarios interesados
6. Clientes actualizan UI: "● Conectado" junto a B
```

### Caso 4: Contacto se Desconecta Mientras Usuario está Activo

```
1. Usuario B se desconecta
2. CS notifica a NS: POST /presence/offline
3. NS actualiza user_presence
4. NS envía SSE a interesados
5. Clientes actualizan UI: "○ Desconectado" junto a B
```

## Notas Adicionales

- El sistema no usa Redis Pub/Sub, eliminando complejidad y puntos de falla
- El estado de presencia se almacena en MySQL, proporcionando persistencia
- El cliente es responsable de consultar el estado al iniciar sesión
- Las notificaciones de cambio de estado se envían via SSE a través del mismo canal que las notificaciones de mensajes
- El sistema soporta múltiples instancias de connection-service
- El estado offline se establece automáticamente cuando el servicio detecta desconexión (sin heartbeat adicional)

## Flujo de Mensajes (API)

### Consulta de Contactos

```json
// Request
GET /api/presence/contacts
Header: X-User-Id: user-uuid-a

// Response
{
  "contacts": [
    {"username": "b", "userId": "user-uuid-b", "status": "ONLINE"},
    {"username": "c", "userId": "user-uuid-c", "status": "OFFLINE"}
  ]
}
```

### Notificación de Conexión

```json
// Request
POST /api/presence/online
{
  "userId": "user-uuid-b",
  "username": "b"
}

// Response
{"status": "success"}
```

### Notificación de Desconexión

```json
// Request  
POST /api/presence/offline
{
  "userId": "user-uuid-b"
}

// Response
{"status": "success"}
```

### Evento SSE de Presencia

```json
// Desde NS hacia Cliente (cuando cambia estado de un contacto)
event: presence
data: {"type":"ONLINE","userId":"user-uuid-b","username":"b"}
```

## Visión General del Flujo Completo

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        FLUJO DE PRESENCIA SIMPLIFICADO                             │
└─────────────────────────────────────────────────────────────────────────────────┘

  USUARIO B                      CS                         NS                        DB
      │                          │                          │                        │
      │  Se conecta             │                          │                        │
      ├─────────────────────────►                          │                        │
      │                          │  POST /presence/online   │                        │
      │                          ├────────────────────────►│                        │
      │                          │                          │  UPDATE user_presence  │
      │                          │                          │────────────────────────►│
      │                          │                          │                        │
      │                          │                          │  SELECT interesados    │
      │                          │                          │  (user_contacts)       │
      │                          │                          │────────────────────────►│
      │                          │                          │                        │
      │                          │  SSE a interesados        │                        │
      │                          │◄─────────────────────────┤                        │
      │                          │                          │                        │
      │                          │                          │                        │
      │ USUARIO A                │                          │                        │
      │ Consulta contactos       │                          │                        │
      ├─────────────────────────► GET /presence/contacts  │                        │
      │                          ├─────────────────────────►│                        │
      │                          │                          │  SELECT contacts       │
      │                          │                          │  + presence status     │
      │                          │                          │────────────────────────►│
      │                          │◄─────────────────────────┤                        │
      │◄────────────────────────┤                          │                        │
      │ Muestra "●" junto a B  │                          │                        │
      │ (contacto online)       │                          │                        │
      │                          │                          │                        │
```

Este flujo simplificado elimina la complejidad del Pub/Sub mientras mantiene la funcionalidad de presencia en tiempo real de forma confiable.