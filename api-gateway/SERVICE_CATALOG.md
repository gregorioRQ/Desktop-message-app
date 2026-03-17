# Service Catalog: API Gateway

## 🎯 Responsabilidad Principal

El **API Gateway** actúa como **punto de entrada único (Single Entry Point)** para todas las solicitudes del cliente. Resuelve tres problemas críticos:

1. **Autenticación Centralizada**: Valida tokens JWT antes de permitir acceso a servicios internos
2. **Enrutamiento Inteligente**: Dirige las peticiones al servicio backend correcto según la ruta solicitada
3. **Gestión de Sesiones**: Mantiene información de sesiones WebSocket en Redis para coordinación entre servicios

---

## 🏗️ Bounded Context (Contexto Delimitado - DDD)

### Categoría: **API Management & Authentication Gateway**

Este servicio pertenece al **grupo de servicios de gestión de acceso y enrutamiento**, siendo un componente transversal que no tiene responsabilidad sobre la lógica de negocio específica, sino sobre el **control de acceso** y la **distribución de tráfico**.

**Límites del Contexto:**
- ✅ Responsable de: Validación de identidad, enrutamiento, sesiones WebSocket
- ❌ NO responsable de: Lógica de autenticación (perfil usuario), lógica de chat, notificaciones, etc.

---

## 📦 Dependencias Clave

### Dependencias Directas (Servicios Backend)

| Servicio | Tipo | Propósito | URL |
|----------|------|----------|-----|
| **Profile Service** | HTTP REST | Autenticación, login, registro | `http://localhost:8088` |
| **Chat Service** | WebSocket Binario | Mensajería en tiempo real | `ws://localhost:8085` |
| **Notification Service** | WebSocket | Notificaciones push en tiempo real | `ws://localhost:8084` |
| **Media Service** | HTTP REST + WebSocket | Gestión y streaming de media | `http://localhost:8089` / `ws://localhost:8089` |

### Dependencias Técnicas

| Componente | Versión | Propósito |
|------------|---------|----------|
| **Spring Cloud Gateway** | 4.x | Framework de enrutamiento y filtros |
| **Redis (Reactivo)** | 6.0+ | Almacenamiento de sesiones WebSocket |
| **JWT (JJWT)** | 0.12.x | Validación de tokens de autenticación |
| **Spring Boot WebFlux** | 3.x | Stack reactivo para manejo de concurrencia |

### Dependencia de Configuración

⚠️ **CRÍTICO**: El JWT SECRET debe ser **idéntico** al usado en Profile Service:
```
SECRET = "w8p3uP3Kz7m2+uFq7y8Zx9cD0y1WkX9KZk3M0FJH8qE="
```

---

## 🔄 Participación en Flujos Core

### 1️⃣ Flujo: Autenticación y Login de Usuario

**Participantes:**
- Client (APP/Web) → **API Gateway** → Profile Service → Database

**Responsabilidad del Gateway:**
- ❌ NO valida credenciales (le corresponde a Profile Service)
- ✅ **Actúa como proxy**: Reenvía la petición `/api/v1/auth/login` a Profile Service
- ✅ **Retorna el token**: Entrega el JWT al cliente
- ⚠️ **NO protege esta ruta**: Es pública (en `openApiEndpoints`)

**Diagrama:**
```
Client POST /api/v1/auth/login 
  ↓ (sin autenticación requerida)
API Gateway (falta autenticación, es puerta abierta)
  ↓
Profile Service (valida credenciales, genera JWT)
  ↓ JWT retornado al cliente
```

---

### 2️⃣ Flujo: Chat en Tiempo Real (WebSocket Binario)

**Participantes:**
- Client (mediante WebSocket) → **API Gateway** → Chat Service → Database

**Responsabilidad del Gateway:**
- ✅ **Autentica la conexión WebSocket**: Valida token JWT en headers
- ✅ **Crea sesión en Redis**: Almacena `ws:sessionid:{userId}` con TTL de 2 minutos
- ✅ **Detecta desconexiones**: Limpia la sesión cuando el WebSocket se cierra
- ✅ **Enruta al Chat Service**: Establece túnel WebSocket binario a `ws://localhost:8085`

**Diagrama:**
```
Client WebSocket Connection /ws-binary/rooms/{roomId}
  ↓ (envía token JWT)
API Gateway [AuthenticationFilter]
  ├─ Valida JWT
  ├─ Extrae userId
  └─ Crea sesión Redis: ws:sessionid:{userId} = sessionId (TTL: 2 min)
  ↓
Chat Service (mantiene conexión WebSocket activa)
  ↓
Base de datos (guarda mensajes)
```

**Interacción con Heartbeat:**
- El cliente envía `/api/v1/auth/heartbeat` cada 30 segundos
- Gateway renueva el TTL de la sesión Redis
- Si expira → cliente debe reconectarse

---

### 3️⃣ Flujo: Notificaciones en Tiempo Real

**Participantes:**
- Client (mediante WebSocket) → **API Gateway** → Notification Service → Message Queue

**Responsabilidad del Gateway:**
- ✅ **Autentica la conexión**: Valida token JWT
- ✅ **Crea sesión en Redis**: Igual que Chat Service
- ✅ **Enruta al Notification Service**: Establece túnel WebSocket a `ws://localhost:8084`

**Nota:** Similar al flujo de Chat, pero destinado a notificaciones en lugar de mensajes directos.

---

### 4️⃣ Flujo: Gestión de Medios (Subida/Descarga)

**Participantes:**
- Client (HTTP/WebSocket) → **API Gateway** → Media Service → File Storage

**Responsabilidad del Gateway:**

**Para peticiones HTTP:**
- ✅ **Autentica petición**: Valida JWT
- ✅ **Añade header X-User-Id**: Propaga la identidad del usuario a Media Service
- ✅ **Enruta a Media Service**: Proxy HTTP a `http://localhost:8089/api/v1/media/**`

**Para WebSocket (streaming):**
- ✅ **Autentica conexión**: Valida JWT
- ✅ **Crea sesión Redis**: Para tracking del stream
- ✅ **Enruta WebSocket**: A `ws://localhost:8089/ws-media/**`

**Diagrama:**
```
Client POST /api/v1/media/upload + JWT
  ↓
API Gateway [AuthenticationFilter]
  ├─ Valida JWT
  ├─ Extrae userId
  └─ Añade header X-User-Id: {userId}
  ↓
Media Service (procesa upload)
  ↓
Almacenamiento de archivos (S3, local, etc.)
```

---

### 5️⃣ Flujo: Mantener Sesión Viva (Heartbeat)

**Participantes:**
- Client → **API Gateway** → Redis → (otros servicios observan sesión)

**Responsabilidad del Gateway:**
- ✅ **Endpoint local**: `/api/v1/auth/heartbeat` se ejecuta dentro del Gateway
- ✅ **Renueva sesión**: Prolonga el TTL de `ws:sessionid:{userId}` en Redis (2 minutos)
- ✅ **Detecta inactividad**: Si la sesión no existe → devuelve 401 Unauthorized

**Flujo:**
```
Client GET /api/v1/auth/heartbeat?X-User-Id=user123
  ↓
API Gateway (HeartbeatController)
  ├─ Busca en Redis: ws:sessionid:user123
  ├─ Si existe: EXPIRE (re-establece TTL a 2 minutos) → 200 OK
  └─ Si NO existe: 401 Unauthorized (sesión expirada)
```

**Propósito:** Mantener la sesión WebSocket activa mientras el cliente esté conectado. Otros servicios pueden monitorear esta clave para saber si un usuario sigue conectado.

---

## 🔐 Modelo de Seguridad

### Rutas Públicas (Sin autenticación)
```
GET  /api/v1/auth/register  → Profile Service
POST /api/v1/auth/login     → Profile Service
GET  /eureka                → Eureka Discovery
```

### Rutas Protegidas (Requieren JWT válido)
```
GET  /api/v1/auth/heartbeat         → HeartbeatController
POST /api/v1/auth/**                → Profile Service (otros endpoints)
WS   /ws-binary/**                  → Chat Service
WS   /ws/**                         → Notification Service
GET  /api/v1/media/**               → Media Service
WS   /ws-media/**                   → Media Service
```

### Validación de Token
1. **Extrae** el token del header `Authorization: Bearer {token}`
2. **Parsea** con clave secreta (Base64 decodificada)
3. **Valida firma** (HMAC-SHA256)
4. **Extrae userId** del claim `subject` (sub)
5. **Propaga** como header `X-User-Id` al servicio backend

---

## 🏃 Stack Técnico

### Framework y Librerías
- **Spring Boot 3.x** + **WebFlux** (reactivo, no bloqueante)
- **Spring Cloud Gateway** (enrutamiento, filtros)
- **JJWT** (validación de JWT)
- **Spring Data Redis Reactive** (manejo async de sesiones)

### Patrones Implementados
- **Gateway Pattern**: Punto de entrada único
- **BFF (Backend for Frontend)**: Abstrae la complejidad de múltiples servicios
- **Reactive Programming**: Con Project Reactor y Mono/Flux
- **Circuit Breaker Ready**: Compatible con Resilience4j

---

## 📊 Métricas y Observabilidad

### Logs Importantes (Verificables en logs)

```
✅ "Ruta pública, no requiere autenticación"
✅ "Ruta segura detectada: /api/..."
⚠️  "Token válido. Usuario: {userId}"
❌ "Acceso denegado: Token expirado"
❌ "Acceso denegado: Token inválido"
✅ "Guardando sesión WebSocket en Redis"
✅ "Logout detectado. Eliminando sesión Redis"
✅ "Conexión exitosa con Redis establecida"
```

### Health Checks
- `GET /actuator/health` → Estado de la aplicación
- Redis connection validated → Startup check automático

---

## 🔄 Flujo de Vida de una Sesión WebSocket

```
1. Cliente conecta a /ws-binary/rooms/{roomId} con JWT
   ↓
2. AuthenticationFilter valida JWT → extrae userId
   ↓
3. handleWebSocketSession() crea en Redis:
   - Key: ws:sessionid:{userId}
   - Value: UUID único (sessionId)
   - TTL: 2 minutos
   ↓
4. Cliente envía heartbeat cada 30 segundos
   ↓
5. HeartbeatController renueva TTL a 2 minutos
   ↓
6. Cliente se desconecta O TTL expira
   ↓
7. handleLogout() o timeout → borra clave de Redis
```

---

## 🚀 Deployment y Configuración

### Variables de Entorno
```bash
REDIS_HOST=localhost        # Redis server host
REDIS_PORT=6379            # Redis server port
PROFILE_SERVICE_URL=http://localhost:8088
CHAT_SERVICE_URL=ws://localhost:8085
NOTIFICATION_SERVICE_URL=ws://localhost:8084
MEDIA_SERVICE_URL=http://localhost:8089
MEDIA_SERVICE_WS_URL=ws://localhost:8089
```

### Secrets
```bash
JWT_SECRET=w8p3uP3Kz7m2+uFq7y8Zx9cD0y1WkX9KZk3M0FJH8qE=
# ⚠️ DEBE coincidir con el secreto en Profile Service
```

---

## 📝 Responsabilidades Resumidas

| Aspecto | Responsabilidad |
|--------|-----------------|
| **Autenticación de credenciales** | ❌ Profile Service |
| **Validación de JWT** | ✅ **API Gateway** |
| **Enrutamiento de peticiones** | ✅ **API Gateway** |
| **Gestión de sesiones WebSocket** | ✅ **API Gateway** + Redis |
| **Lógica de chat** | ❌ Chat Service |
| **Lógica de notificaciones** | ❌ Notification Service |
| **Procesamiento de medios** | ❌ Media Service |
| **Renovación de sesión (heartbeat)** | ✅ **API Gateway** |

---

## 🔗 Referencias y Documentación Relacionada

- [Spring Cloud Gateway Documentation](https://cloud.spring.io/spring-cloud-gateway/reference/html/)
- [JJWT - JWT for Java](https://github.com/jwtk/jjwt)
- [Domain-Driven Design (DDD Concepts)](https://martinfowler.com/bliki/DomainDrivenDesign.html)
- [API Gateway Pattern](https://microservices.io/patterns/apigateway.html)
- [Reactive Programming with Project Reactor](https://projectreactor.io/)

---

## ✅ Checklist de Validación

Antes de desplegar cambios al API Gateway, verificar:

- [ ] JWT_SECRET es idéntico al de Profile Service
- [ ] Redis está disponible y accesible
- [ ] Todos los servicios backend están configurados en `application.properties`
- [ ] Los logs muestran "Conexión exitosa con Redis establecida"
- [ ] Pruebas de autenticación exitosas (JWT válido → 200 OK)
- [ ] Pruebas de rechazo exitosas (JWT inválido → 401 Unauthorized)
- [ ] Sesiones WebSocket se crean y se limpian correctamente en Redis
- [ ] Heartbeat renueva sesiones correctamente

