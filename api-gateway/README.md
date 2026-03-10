# API Gateway - Microservices Routing & Authentication

Punto de entrada único (Single Entry Point) para todos los servicios backend en la arquitectura de microservicios. Proporciona autenticación centralizada, enrutamiento inteligente y gestión de sesiones WebSocket.

## 🚀 Inicio Rápido

### Prerrequisitos
- Java 17+
- Maven 3.8+
- Docker & Docker Compose (para Redis)
- Servicios backend ejecutándose en puertos configurados

### Instalación y Ejecución

```bash
# Clonar el repositorio
git clone <repo-url>
cd api-gateway

# Construir el proyecto
mvn clean install

# Ejecutar la aplicación
mvn spring-boot:run
```

El gateway estará disponible en `http://localhost:8080`

## 📋 Tabla de Contenidos

1. [Responsabilidades](#responsabilidades)
2. [Rutas Disponibles](#rutas-disponibles)
3. [Configuración](#configuración)
4. [Autenticación JWT](#autenticación-jwt)
5. [Gestión de Sesiones WebSocket](#gestión-de-sesiones-websocket)
6. [Participación en Flujos](./SERVICE_CATALOG.md#-participación-en-flujos-core)
7. [Troubleshooting](#troubleshooting)

---

## 🎯 Responsabilidades

✅ **Autenticación Centralizada**
- Válida tokens JWT en todas las rutas protegidas
- Rechaza peticiones sin autenticación válida (401 Unauthorized)

✅ **Enrutamiento Inteligente**
- Dirige peticiones HTTP REST a los servicios correctos
- Maneja túneles WebSocket para conexiones en tiempo real

✅ **Gestión de Sesiones**
- Almacena sesiones WebSocket en Redis
- Detecta desconexiones y limpia recursos

✅ **Propagación de Identidad**
- Añade header `X-User-Id` para que servicios backend conozcan el usuario

---

## 🛣️ Rutas Disponibles

### Rutas Públicas (Sin autenticación requerida)

```
POST   /api/v1/auth/register              → Profile Service [Registro de usuario]
POST   /api/v1/auth/login                 → Profile Service [Autenticación]
GET    /eureka                            → Eureka Discovery [Descubrimiento de servicios]
```

### Rutas Protegidas (JWT requerido)

#### Autenticación y Perfil Existentes
```
GET    /api/v1/auth/heartbeat             → HeartbeatController [Renovar sesión]
GET    /api/v1/auth/profile               → Profile Service [Obtener perfil]
PUT    /api/v1/auth/profile               → Profile Service [Actualizar perfil]
```

#### Chat (WebSocket Binario)
```
WS     /ws-binary/rooms/{roomId}          → Chat Service [Mensajería en tiempo real]
WS     /ws-binary/**                      → Chat Service [Otros endpoints WebSocket]
```

#### Notificaciones (WebSocket)
```
WS     /ws/notifications                  → Notification Service [Notificaciones push]
WS     /ws/**                             → Notification Service [Otros endpoints WebSocket]
```

#### Media (REST + WebSocket)
```
GET    /api/v1/media/files/{fileId}       → Media Service [Descargar archivo]
POST   /api/v1/media/upload               → Media Service [Subir archivo]
DELETE /api/v1/media/files/{fileId}       → Media Service [Eliminar archivo]
WS     /ws-media/stream/{fileId}          → Media Service [Streaming de media]
```

---

## ⚙️ Configuración

### application.properties

```properties
# Puerto del gateway
server.port=8080

# Configuración de Redis
spring.data.redis.host=localhost        # Host de Redis (variable de entorno: REDIS_HOST)
spring.data.redis.port=6379             # Puerto de Redis (variable de entorno: REDIS_PORT)

# URLs de servicios backend
PROFILE_SERVICE_URL=http://localhost:8088
CHAT_SERVICE_URL=ws://localhost:8085
NOTIFICATION_SERVICE_URL=ws://localhost:8084
MEDIA_SERVICE_URL=http://localhost:8089
MEDIA_SERVICE_WS_URL=ws://localhost:8089
```

### Variables de Entorno

```bash
# Redis
export REDIS_HOST=redis-container
export REDIS_PORT=6379

# Servicios Backend
export PROFILE_SERVICE_URL=http://profile-service:8088
export CHAT_SERVICE_URL=ws://chat-service:8085
export NOTIFICATION_SERVICE_URL=ws://notification-service:8084
export MEDIA_SERVICE_URL=http://media-service:8089
export MEDIA_SERVICE_WS_URL=ws://media-service:8089
```

### Docker Compose

```yaml
version: '3.8'

services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    environment:
      - REDIS_PASSWORD=tu_password_seguro

  api-gateway:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    depends_on:
      - redis
    environment:
      REDIS_HOST: redis
      REDIS_PORT: 6379
      PROFILE_SERVICE_URL: http://profile-service:8088
      CHAT_SERVICE_URL: ws://chat-service:8085
      NOTIFICATION_SERVICE_URL: ws://notification-service:8084
      MEDIA_SERVICE_URL: http://media-service:8089
      MEDIA_SERVICE_WS_URL: ws://media-service:8089
```

---

## 🔐 Autenticación JWT

### Flujo de Autenticación

1. **Cliente se registra**: `POST /api/v1/auth/register`
2. **Cliente inicia sesión**: `POST /api/v1/auth/login` → Recibe JWT en respuesta
3. **Cliente incluye JWT**: En header `Authorization: Bearer {token}`
4. **Gateway valida**: Verifica firma y expiración del token
5. **Gateway propaga identidad**: Añade header `X-User-Id` con el userId extraído

### Estructura del JWT

```
Header:  { "alg": "HS256", "typ": "JWT" }
Payload: { "sub": "user-id-123", "exp": 1234567890, ... }
Signature: HMAC-SHA256(header.payload, SECRET)
```

### Secreto Compartido

⚠️ **CRÍTICO**: El JWT_SECRET debe ser **idéntico** en todos los servicios:

```java
// api-gateway/JwtUtil.java
public static final String SECRET = "w8p3uP3Kz7m2+uFq7y8Zx9cD0y1WkX9KZk3M0FJH8qE=";

// profile-service/JwtUtil.java (DEBE SER IGUAL)
public static final String SECRET = "w8p3uP3Kz7m2+uFq7y8Zx9cD0y1WkX9KZk3M0FJH8qE=";
```

### Errores de Autenticación

```
401 Unauthorized
  - Header Authorization faltante
  - Token inválido o corrupto
  - Token expirado
  - Firma inválida

403 Forbidden
  - Token válido pero usuario sin permisos (futuro)
```

---

## 🔄 Gestión de Sesiones WebSocket

### Flujo de Conexión WebSocket

```
1. Cliente abre conexión: WS /ws-binary/rooms/123?token=eyJhbGc...
2. Gateway AuthenticationFilter:
   ├─ Valida JWT
   ├─ Extrae userId
   └─ Crea en Redis: ws:sessionid:{userId} = {sessionId}
3. Túnel WebSocket activo hacia Chat Service
4. TTL de sesión: 2 minutos (se renueva con heartbeat)
```

### Heartbeat para Mantener Sesión Viva

Los clientes deben enviar heartbeat cada **30-60 segundos** para evitar que la sesión expire:

```bash
# Cliente debe ejecutar cada 30 segundos
GET http://localhost:8080/api/v1/auth/heartbeat
Header: X-User-Id: {userId}
Header: Authorization: Bearer {token}
```

**Respuestas:**
```
200 OK "Alive"                           → Sesión renovada
401 Unauthorized "Session expired..."    → Token o sesión expirada, reconectar
```

### Limpiar Sesión en Logout

```bash
# Cliente enviando header LOGOUT
GET http://localhost:8080/api/v1/auth/heartbeat
Header: LOGOUT: true
```

---

## 📊 Monitoreo y Debugging

### Health Check

```bash
curl http://localhost:8080/actuator/health

# Respuesta esperada
{
  "status": "UP",
  "components": {
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0",
        "...": "..."
      }
    }
  }
}
```

### Logs Importantes

```
# Inicio correcto
INFO: Conexión exitosa con Redis establecida
INFO: Redis PING respondió: PONG

# Validación de ruta
DEBUG: Ruta pública, no requiere autenticación: /api/v1/auth/login
DEBUG: Ruta segura detectada: /api/v1/auth/heartbeat

# Validación de token
INFO: Token válido. Usuario: user-123
WARN: Limpiando sesión Redis por expiración para usuario: user-123
ERROR: Acceso denegado: Token expirado

# Gestión de sesiones WebSocket
INFO: Guardando sesión WebSocket en Redis - Usuario: user-123, SessionId: abc-def-xyz
INFO: Logout detectado. Eliminando sesión Redis para usuario: user-123
```

### Inspeccionar Redis

```bash
# Conectar a Redis
docker exec -it redis-container redis-cli

# Ver todas las sesiones WebSocket activas
KEYS ws:sessionid:*

# Ver detalles de una sesión
GET ws:sessionid:user-123
TTL ws:sessionid:user-123
```

---

## 🐛 Troubleshooting

### Error: "Conexión rechazada a Redis"

```
java.net.ConnectException: Connection refused
```

**Soluciones:**
- Verificar que Redis está corriendo: `docker ps | grep redis`
- Verificar REDIS_HOST y REDIS_PORT en application.properties
- Reiniciar Redis: `docker restart redis-container`

---

### Error: "Acceso denegado: Token inválido"

```
401 Unauthorized
Acceso denegado: Token inválido
```

**Causas posibles:**
- JWT_SECRET no coincide entre gateway y profile-service
- Token corrupto o truncado
- Token de otro servicio

**Soluciones:**
- Verificar JWT_SECRET en ambos servicios (debe ser idéntico)
- Generar un nuevo token en login
- Verificar que el token se envía completo en header Authorization

---

### Error: "Falta header de autorización"

```
401 Unauthorized
Falta header de autorización
```

**Solución:**
Incluir header en la petición:
```bash
curl -H "Authorization: Bearer {token}" http://localhost:8080/api/v1/auth/profile
```

---

### WebSocket desconecta después de 1-2 minutos

**Causa:** La sesión expira en Redis (TTL = 2 minutos)

**Solución:** Cliente debe enviar heartbeat cada 30-60 segundos
```javascript
// JavaScript/TypeScript
setInterval(() => {
  fetch('http://localhost:8080/api/v1/auth/heartbeat', {
    headers: {
      'Authorization': `Bearer ${token}`,
      'X-User-Id': userId
    }
  });
}, 30000); // Cada 30 segundos
```

---

### rutas no enrutadas correctamente

**Verificar en application.properties:**
1. Índice de ruta correcto (routes[0], routes[1], etc.)
2. Predicados Path coinciden con la solicitud
3. URI del servicio backend es correcta
4. AuthenticationFilter está en filters

---

## 📚 Documentación Detallada

Para información más profunda sobre:
- **Bounded Context y responsabilidades**: Ver [SERVICE_CATALOG.md](./SERVICE_CATALOG.md)
- **Participación en flujos core**: Ver [SERVICE_CATALOG.md#-participación-en-flujos-core](./SERVICE_CATALOG.md#-participación-en-flujos-core)
- **Dependencias entre servicios**: Ver [SERVICE_CATALOG.md#-dependencias-clave](./SERVICE_CATALOG.md#-dependencias-clave)

---

## 🗂️ Estructura del Proyecto

```
api-gateway/
├── src/main/java/com/pola/api_gateway/
│   ├── ApiGatewayApplication.java         # Punto de entrada
│   ├── controller/
│   │   └── HeartbeatController.java       # Endpoint de heartbeat
│   ├── filter/
│   │   └── AuthenticationFilter.java      # Validación de JWT
│   ├── config/
│   │   ├── RouteValidator.java            # Definición de rutas públicas
│   │   └── RedisHealthCheck.java          # Validación de Redis
│   └── util/
│       └── JwtUtil.java                   # Utilidades de JWT
├── src/main/resources/
│   └── application.properties              # Configuración de rutas y servicios
├── SERVICE_CATALOG.md                      # Catálogo detallado del servicio
├── README.md                               # Este archivo
├── pom.xml                                 # Dependencias Maven
└── Dockerfile                              # Imagen Docker

```

---

## 🔗 Dependencias del Proyecto

| Dependencia | Versión | Propósito |
|------------|---------|----------|
| Spring Boot | 3.x | Framework base |
| Spring Cloud Gateway | 4.x | Enrutamiento y filtros |
| Spring Data Redis Reactive | 3.x | Sesiones async en Redis |
| JJWT | 0.12.x | Validación de JWT |
| Spring WebFlux | 3.x | Stack reactivo |
| SLF4J + Logback | Estándar | Logging |

---

## 🚀 Próximos Pasos

### Mejoras Futuras
- [ ] Implementar Circuit Breaker para servicios backend
- [ ] Añadir rate limiting por usuario
- [ ] Implementar caching de sesiones
- [ ] Integrar con Eureka para descubrimiento de servicios
- [ ] Métricas exportadas a Prometheus
- [ ] Trazas distribuidas con Jaeger

### Para Desarrolladores
1. Revisar [SERVICE_CATALOG.md](./SERVICE_CATALOG.md) para entender el diseño
2. Analizar `AuthenticationFilter.java` para entender la validación
3. Consultar `application.properties` para entender el enrutamiento
4. Ejecutar tests: `mvn test`

---

## 📞 Contacto y Soporte

- **Documentación de Spring Cloud Gateway**: https://cloud.spring.io/spring-cloud-gateway/
- **Documentación de JJWT**: https://github.com/jwtk/jjwt
- **Domain-Driven Design (DDD)**: https://martinfowler.com/bliki/DomainDrivenDesign.html

---

## 📄 Licencia

Este proyecto es parte de la arquitectura de microservicios interna. Todos los derechos reservados.

