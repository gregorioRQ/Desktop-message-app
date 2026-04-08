# Flujo de Login y Refresh Tokens

Este documento describe el flujo completo de autenticación, desde que el usuario ingresa sus credenciales hasta el manejo de tokens cuando estos expiran. El sistema utiliza JWT para el acceso y tokens opacos para el refresh.

---

## 1. Arquitectura del Sistema de Autenticación

### Servicios Involucrados

| Servicio | Puerto | Responsabilidad |
|----------|--------|-----------------|
| profile-service | 8088 | Gestión de usuarios, generación y validación de tokens |
| api-gateway | 8080 | Validación de tokens JWT en todas las rutas protegidas |
| connection-service | 8083 | Registro de sesiones WebSocket, mapeo de usuarios en Redis |
| websocket-client | N/A | Cliente JavaFX que gestiona autenticación local |

### Endpoints REST

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Registro de nuevos usuarios |
| POST | `/api/v1/auth/login` | Inicio de sesión |
| POST | `/api/v1/auth/refresh` | Renovación de access token |
| POST | `/api/v1/auth/logout` | Cierre de sesión |

**Nota**: El API-Gateway permite acceso sin autenticación solo a `/api/v1/auth/register` y `/api/v1/auth/login`.

---

## 2. Flujo Detallado Paso a Paso

### Paso 1: Registro de Usuario

El usuario nuevo se registra en el sistema proporcionando username y contraseña.

**Qué ocurre:**
- El cliente JavaFX envía un `RegisterRequest` en formato Protobuf
- El ProfileController recibe la solicitud y la procesa
- El ProfileServiceImpl ejecuta la validación y creación del usuario

**Validaciones realizadas:**
- El CredentialsValidator verifica que las credenciales cumplan los requisitos
- Se verifica que el username no esté ya en uso

**Almacenamiento:**
- La contraseña se hashea usando BCrypt antes de persistir
- Se crea una entidad User con los datos del nuevo usuario

**Respuesta:**
- `RegisterResponse` con código 200 si el registro fue exitoso
- Error 400 si las credenciales no son válidas

---

### Paso 2: Login - Envío de Credenciales

El usuario existente inicia sesión con sus credenciales.

**Qué ocurre:**
- El cliente JavaFX construye un `LoginRequest` con los siguientes campos:
  - `username`: Nombre de usuario
  - `password`: Contraseña en texto plano
  - `deviceId`: Identificador único del dispositivo

**Envío de solicitud:**
- El HttpServiceImpl envía POST a `/api/v1/auth/login`
- Header `Content-Type: application/x-protobuf`
- El cuerpo contiene el LoginRequest serializado en Protobuf

---

### Paso 3: Autenticación en ProfileService

El servidor valida las credenciales y genera los tokens.

**Qué ocurre:**
- El ProfileServiceImpl recibe el LoginRequest
- Llama al método privado `authenticate()` que:
  - Busca el usuario en la base de datos por nombre de usuario
  - Verifica la contraseña usando `passwordEncoder.matches()`
  
**Resultado de la autenticación:**
- **Fallida**: Retorna error 401 "Usuario o contraseña incorrectos"
- **Exitosa**: Continúa al proceso de generación de tokens

---

### Paso 4: Generación de Tokens

El sistema crea el access token y refresh token para el usuario.

**Qué ocurre:**
- El JwtService genera el access token mediante `generateToken(user)`
- El JwtService genera el refresh token mediante `generateRefreshToken(user, deviceId)`

**Access Token (JWT):**
- Es un JWT firmado con HMAC-SHA256
- Header: `{"alg": "HS256", "typ": "JWT"}`
- Payload con claims:
  - `sub`: ID del usuario (userId)
  - `username`: Nombre de usuario
  - `iat`: Tiempo de emisión
  - `exp`: Tiempo de expiración

**Refresh Token (Opaco):**
- No es un JWT, es un token aleatorio generado concatenando dos UUID sin guiones:
  ```
  UUID.randomUUID().toString().replace("-", "") + 
  UUID.randomUUID().toString().replace("-", "")
  ```
- Se almacena en la base de datos del profile-service
- Permite revocación inmediata si es necesario

**Clave secreta:**
- La clave para firmar el JWT está definida en JwtService:
  ```java
  private static final String SECRET_KEY = "w8p3uP3Kz7m2+uFq7y8Zx9cD0y1WkX9KZk3M0FJH8qE=";
  ```
- Esta misma clave se utiliza en el API-Gateway para validar tokens

---

### Paso 5: Respuesta al Cliente

El servidor retorna los tokens al cliente.

**Qué ocurre:**
- El ProfileController retorna un `LoginResponse` con código 200
- El contenido incluye:
  - `accessToken`: El token JWT
  - `refreshToken`: El token opaco
  - Datos del usuario (userId, username)

**Si la autenticación falla:**
- Retorna código 401 con mensaje de error

---

### Paso 6: Almacenamiento Local de Sesión

El cliente guarda los tokens de forma segura en el dispositivo.

**Qué ocurre:**
- El cliente recibe el LoginResponse
- Crea un objeto Session con los tokens y datos del usuario
- Guarda la sesión en una base de datos SQLite local (`session.db`)

**Estructura de la tabla:**
```sql
CREATE TABLE session (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    access_token TEXT,
    refresh_token TEXT,
    user_id TEXT,
    username TEXT,
    device_id TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
)
```

**Seguridad:**
- Los tokens se encriptan usando AES antes de almacenarse
- La clave de encriptación se deriva mediante SHA-256
- Esto protege los tokens en caso de acceso no autorizado al dispositivo

---

### Paso 7: Conexión WebSocket

El cliente se conecta al servicio de conexión en tiempo real.

**Qué ocurre:**
- El WebSocketServiceImpl establece la conexión WebSocket
- Envía headers especiales en el handshake:
  - `X-User-ID`: El ID del usuario
  - `X-Username`: El nombre de usuario
  - `Authorization`: El token JWT con formato "Bearer <token>"

**Procesamiento en connection-service:**
- El ConnectionWebSocketHandler procesa el handshake:
  - Valida que estén presentes los headers `X-User-ID` y `X-Username`
  - Si faltan, cierra la conexión con `CloseStatus.POLICY_VIOLATION`
  - Registra la sesión en SessionRegistryService
  - Almacena el mapeo username → userId en Redis

**Clave Redis creada:**
- `user:name:{username}` → userId
- Esto permite al MessageRouterService determinar dónde está conectado el usuario

---

### Paso 8: Detección de Token Expirado

Cuando el access token expira, el sistema debe renovar el acceso.

**Qué ocurre:**
- El cliente intenta hacer una solicitud HTTP con el access token
- El API-Gateway valida el token mediante AuthenticationFilter
- Si el token está expirado, lanza `ExpiredJwtException`
- El Gateway retorna HTTP 401 con mensaje "Acceso denegado: Token expirado"

**Detección en cliente:**
- El HttpInterceptor o handling de respuestas detecta el error 401
- Se dispara el proceso de refresh token

---

### Paso 9: Renovación del Access Token

El cliente renueva el token de acceso automáticamente.

**Qué ocurre:**
- El cliente envía `RefreshRequest` con el `refreshToken` actual
- La solicitud se hace a `POST /api/v1/auth/refresh`

**Procesamiento en JwtService:**
- Busca el refresh token en la base de datos
- Verifica que no haya expirado (compara con la fecha actual)
- Recupera el usuario asociado al token
- Genera un nuevo access token
- Retorna el nuevo access token junto con el refresh token existente

**Respuesta exitosa:**
```protobuf
RefreshResponse {
  accessToken: "nuevo_jwt"
  refreshToken: "refresh_token_existente"
  errorMsg: ""
}
```

**Respuesta fallida:**
```protobuf
RefreshResponse {
  accessToken: ""
  refreshToken: ""
  errorMsg: "El token ha expirado" // u otro mensaje de error
}
```

**Actualización local:**
- El cliente guarda la nueva sesión con el nuevo access token
- El refresh token permanece igual (no se renueva)

---

### Paso 10: Fallo en Refresh Token

Si el refresh token también ha expirado o es inválido.

**Qué ocurre:**
- El JwtService lanza `TokenExpiredException` o `TokenNotFoundException`
- El servicio retorna un RefreshResponse con tokens vacíos
- El mensaje de error indica la causa específica

**Acciones del cliente:**
- Detecta que el access token está vacío
- Limpia la sesión local mediante `tokenRepository.clearSession()`
- El usuario debe iniciar sesión nuevamente

---

## 3. Duración de los Tokens

### Access Token
```
Duración: 24 horas (86400000 milisegundos)
```

El tiempo de expiración está configurado en JwtService:
```java
private static final long EXPIRATION_TIME = 86400000; // 24 horas
```

### Refresh Token
```
Duración: 30 días
```

El refresh token se almacena en la base de datos con:
```java
refreshToken.setExpiryDate(LocalDateTime.now().plusDays(30));
```

---

## 4. Almacenamiento de Tokens

### Refresh Tokens en Base de Datos

El profile-service utiliza JPA para almacenar los refresh tokens en la tabla gestionada por la entidad `RefreshToken`:

| Campo | Tipo | Descripción |
|-------|------|-------------|
| id | Long | Identificador único |
| token | String | El token opaco |
| userId | String | ID del usuario propietario |
| deviceId | String | Identificador del dispositivo |
| expiryDate | LocalDateTime | Fecha de expiración |
| createdAt | LocalDateTime | Fecha de creación |

### Sesión en Cliente JavaFX

El cliente almacena la sesión en SQLite local (`session.db`) con encriptación AES-128.

### Mapeo de Usuarios en Redis

El connection-service utiliza Redis para rastrear la conexión de cada usuario:
- **Clave**: `user:name:{username}`
- **Valor**: userId del usuario

---

## 5. Errores y Manejo

### Errores en el Login

| Código HTTP | Mensaje | Causa Posible |
|-------------|---------|---------------|
| 400 | Error de validación | Credenciales inválidas o vacías |
| 401 | Usuario o contraseña incorrectos | Credenciales no coinciden |
| 500 | Error interno durante el login | Error de base de datos o servidor |

### Errores en el Refresh Token

| Escenario | Respuesta del Servidor | Acción del Cliente |
|-----------|------------------------|--------------------|
| Token no encontrado | `{accessToken: "", refreshToken: "", errorMsg: "Refresh token inválido o no encontrado"}` | Limpiar sesión, pedir login |
| Token expirado | `{accessToken: "", refreshToken: "", errorMsg: "El token ha expirado"}` | Limpiar sesión, pedir login |
| Usuario no encontrado | `{accessToken: "", refreshToken: "", errorMsg: "Usuario no encontrado"}` | Limpiar sesión, pedir login |
| Error interno | `{accessToken: "", refreshToken: "", errorMsg: "No se pudo procesar el refreshToken"}` | Reintentar o pedir login |

### Errores en API-Gateway

| Código HTTP | Mensaje | Causa |
|-------------|---------|-------|
| 401 | Falta header de autorización | No se incluyó el header Authorization |
| 401 | Acceso denegado: Token expirado | El token JWT ha expirado |
| 401 | Acceso denegado: Token inválido | Token malformado o firma inválida |

### Errores en WebSocket

| Código de Cierre | Razón | Causa |
|------------------|-------|-------|
| POLICY_VIOLATION | Missing headers | Faltan X-User-ID o X-Username |
| NORMAL_CLOSURE | - | Cierre normal por el cliente o servidor |

---

## 6. Flujo de Logout

El proceso de logout sigue estos pasos:

**Qué ocurre:**
1. El usuario hace clic en el botón de cerrar sesión
2. El LogoutHandler carga la sesión actual y extrae el refresh token y username
3. Envía una solicitud POST a `/api/v1/auth/logout` con:
   - `refreshToken`: El token de actualización
   - `username`: El nombre de usuario
   - Header `Authorization: Bearer <accessToken>`
   - Header adicional `LOGOUT: true`

**En profile-service:**
- El ProfileServiceImpl elimina el refresh token de la base de datos mediante `jwtService.deleteRefreshToken()`
- Publica un evento a RabbitMQ para notificar a connection-service

**En connection-service:**
- El UserLogoutConsumer recibe el evento
- Elimina la clave Redis `user:name:{username}`
- Esto asegura que los mensajes no se enruten a usuarios desconectados

**En el cliente:**
- Limpia la sesión local mediante `tokenRepository.clearSession()`
- Desconecta el WebSocket

---

## 7. Diagrama del Flujo

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           REGISTRO DE USUARIO                                │
└─────────────────────────────────────────────────────────────────────────────┘

[Usuario nuevo]
       │
       │ POST /api/v1/auth/register (RegisterRequest)
       ▼
┌──────────────────┐
│ profile-service  │
│                  │
│ 1. Credentials  │
│    Validator     │
│ 2. BCrypt hash   │
│ 3. Persist User  │
└──────────────────┘
       │
       │ RegisterResponse (200 o error)
       ▼
[Usuario registrado]


┌─────────────────────────────────────────────────────────────────────────────┐
│                              LOGIN                                           │
└─────────────────────────────────────────────────────────────────────────────┘

[Usuario existente]
       │
       │ POST /api/v1/auth/login (LoginRequest)
       ▼
┌──────────────────┐
│ profile-service  │
│                  │
│ 1. Busca usuario │
│ 2. Verifica      │
│   contraseña     │
│ 3. Genera tokens │
└──────────────────┘
       │
       │ LoginResponse (accessToken + refreshToken)
       ▼
[Cliente recibe tokens]


┌─────────────────────────────────────────────────────────────────────────────┐
│                      ALMACENAMIENTO LOCAL                                    │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente]
       │
       │ 1. Crea objeto Session
       │ 2. Encripta con AES
       │ 3. Guarda en SQLite (session.db)
       ▼
[Sesión almacenada]


┌─────────────────────────────────────────────────────────────────────────────┐
│                      CONEXIÓN WEBSOCKET                                       │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente]
       │
       │ WebSocket CONNECT
       │ Headers: X-User-ID, X-Username, Authorization: Bearer <token>
       ▼
┌──────────────────────┐
│ connection-service   │
│                      │
│ 1. Valida headers    │
│ 2. SessionRegistry   │
│ 3. Redis: user:name: │
│    {username}->userId│
└──────────────────────┘


┌─────────────────────────────────────────────────────────────────────────────┐
│                         REFRESH TOKEN                                        │
└─────────────────────────────────────────────────────────────────────────────┘

[Access token expira]
       │
       │ API-Gateway retorna 401 "Token expirado"
       ▼
[Cliente detecta error]
       │
       │ POST /api/v1/auth/refresh (RefreshRequest)
       ▼
┌──────────────────┐
│ profile-service  │
│                  │
│ 1. Busca refresh  │
│    token en BD    │
│ 2. Verifica       │
│    expiración     │
│ 3. Genera nuevo   │
│    access token   │
└──────────────────┘
       │
       │ RefreshResponse (nuevo accessToken)
       ▼
[Cliente actualiza sesión]


┌─────────────────────────────────────────────────────────────────────────────┐
│                              LOGOUT                                          │
└─────────────────────────────────────────────────────────────────────────────┘

[Usuario hace logout]
       │
       │ POST /api/v1/auth/logout
       ▼
┌──────────────────┐
│ profile-service  │
│                  │
│ 1. Elimina       │
│    refresh token │
│    de BD         │
│ 2. Publica en   │
│    RabbitMQ      │
└──────────────────┘
       │
       │ Evento en RabbitMQ
       ▼
┌──────────────────────┐
│ connection-service   │
│                      │
│ UserLogoutConsumer   │
│ elimina Redis:       │
│ user:name:{username}│
└──────────────────────┘
       │
       ▼
[Cliente limpia sesión local]
[Desconecta WebSocket]
```

---

## 8. Consideraciones de Seguridad

1. **Almacenamiento seguro**: Los tokens en el cliente se encriptan con AES-128 antes de almacenarse en SQLite.

2. **Contraseñas**: Las contraseñas de usuario se hashean usando BCrypt con factor de trabajo apropiado.

3. **Tokens opacos para refresh**: Los refresh tokens no son JWT, son tokens aleatorios almacenados en la base de datos, lo que permite revocación inmediata.

4. **Logout mediante RabbitMQ**: Al hacer logout, se publica un evento a través de RabbitMQ para asegurar que connection-service limpie Redis, incluso si el cliente está en una instancia diferente.

5. **Validación en API-Gateway**: Todas las rutas excepto login y register requieren token JWT válido, incluyendo el endpoint de refresh.

6. **Protección de Headers WebSocket**: El connection-service valida la presencia de X-User-ID y X-Username en el handshake de WebSocket, rechazando conexiones que no incluyan estos headers.