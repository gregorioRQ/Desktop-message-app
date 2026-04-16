# Flujo de Registro de Usuario

Este documento describe el flujo completo de registro de un nuevo usuario en el sistema, desde que el cliente envía la solicitud hasta que el usuario queda registrado en todos los servicios. El sistema utiliza una arquitectura basada en microservicios con Spring Boot, RabbitMQ para eventos asíncronos y BCrypt para el hashing de contraseñas.

---

## 1. Arquitectura General

### Servicios Involucrados

| Servicio | Puerto | Responsabilidad |
|----------|--------|-----------------|
| profile-service | 8088 | Gestión de usuarios, autenticación y registro |
| notification-service | 8084 | Sincronización de usuarios y notificaciones push |
| api-gateway | 8080 | Enrutamiento de solicitudes y validación JWT |
| websocket-client | - | Cliente JavaFX que inicia el registro |

### Exchange de RabbitMQ

El sistema utiliza un exchange directo llamado `user.exchange` para propagar eventos de usuario:

| Cola | Routing Key | Propósito | Productor | Consumidor |
|------|-------------|-----------|-----------|------------|
| `user.created` | `user.created` | Evento de nuevo usuario creado | profile-service | notification-service |
| `user.logout` | `user.logout` | Evento de cierre de sesión | profile-service | connection-service |

### Canales de Comunicación

- **REST API**: Solicitudes HTTP desde el cliente al profile-service a través del API Gateway
- **RabbitMQ**: Mensajería asíncrona para sincronización entre servicios

---

## 2. Flujo Detallado Paso a Paso

### Paso 1: Usuario completa el formulario de registro

El usuario llena el formulario de registro en el cliente JavaFX.

**Qué ocurre:**
- El usuario ingresa username y password en `register.fxml`
- El `RegisterController` valida los campos en el cliente
- Se construye un mensaje protobuf `RegisterRequest`

**Campos del formulario:**
```
username: texto (mínimo 3 caracteres)
password: texto (mínimo 6 caracteres)
confirmPassword: texto (debe coincidir con password)
```

**Validaciones cliente:**
- Username no vacío
- Username con al menos 3 caracteres
- Password no vacío
- Password con al menos 6 caracteres
- Passwords coincidan

---

### Paso 2: Cliente envía solicitud HTTP

El cliente envía la solicitud de registro al API Gateway.

**Qué ocurre:**
- Se construye el `RegisterRequest` protobuf
- Se envía POST a `/api/v1/auth/register`
- El API Gateway recibe la solicitud

**Datos enviados:**
```
POST /api/v1/auth/register
Content-Type: application/x-protobuf

RegisterRequest:
  - username: "nuevo_usuario"
  - password: "contraseña123"
```

---

### Paso 3: API Gateway valida y enruta

El API Gateway determina si la ruta es pública y reenvía al profile-service.

**Qué ocurre:**
- `AuthenticationFilter` verifica si la ruta es pública
- Rutas públicas: `/api/v1/auth/register`, `/api/v1/auth/login`
- La solicitud se reenvía a profile-service (puerto 8088)

**Rutas públicas configuradas:**
- `/api/v1/auth/register`
- `/api/v1/auth/login`
- `/eureka`

---

### Paso 4: profile-service valida credenciales

El `ProfileServiceImpl` valida los datos del usuario.

**Qué ocurre:**
- `CredentialsValidator` verifica el formato del username y password
- Se valida que el username no exista en la base de datos
- Si hay errores, se lanza una excepción con el mensaje correspondiente

**Validaciones realizadas:**
| Validación | Regla | Mensaje de error |
|------------|-------|------------------|
| Longitud username | 3-50 caracteres | "El nombre de usuario debe tener entre 3 y 50 caracteres" |
| Formato username | Solo `a-zA-Z0-9_-` | "El nombre de usuario solo puede contener letras, números, guiones y guiones bajos" |
| Longitud password | Mínimo 6 caracteres | "La contraseña debe tener al menos 6 caracteres" |
| Username único | No existe en BD | "Username existente" |

---

### Paso 5: Se crea el usuario en la base de datos

El sistema persiste el usuario con la contraseña hasheada.

**Qué ocurre:**
- Se genera un UUID para el `userId`
- Se hashea la contraseña con BCrypt
- Se crea el registro en la tabla `users`
- Se guardan timestamps de creación y actualización

**Entidad User creada:**
```java
User user = new User();
user.setId(UUID.randomUUID().toString());
user.setUsername("nuevo_usuario");
user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt()));
user.setCreatedAt(LocalDateTime.now());
user.setUpdatedAt(LocalDateTime.now());
user.setIsActive(true);

userRepository.save(user);
```

**Tabla users (profile-service):**
| Campo | Tipo | Restricciones |
|-------|------|---------------|
| id | VARCHAR(36) | Primary Key |
| username | VARCHAR(50) | UNIQUE, NOT NULL |
| password | VARCHAR(255) | NOT NULL (BCrypt hash) |
| created_at | TIMESTAMP | NOT NULL |
| updated_at | TIMESTAMP | NOT NULL |
| is_active | BOOLEAN | DEFAULT TRUE |

---

### Paso 6: Se publica el evento UserCreatedEvent

El profile-service notifica a los demás servicios del nuevo usuario.

**Qué ocurre:**
- Se crea un `UserCreatedEvent` con los datos del usuario
- Se publica en el exchange `user.exchange` con routing key `user.created`
- El `UserCreatedConsumer` en notification-service recibe el evento

**Estructura del UserCreatedEvent:**
```json
{
  "user_id": "uuid-generado",
  "username": "nuevo_usuario"
}
```

**Flujo RabbitMQ:**
```
profile-service
    ↓ RabbitMQ (user.exchange, user.created)
notification-service
    → UserCreatedConsumer.receive()
    → UserService.create(user)
```

---

### Paso 7: notification-service sincroniza el usuario

El notification-service crea una copia local del usuario.

**Qué ocurre:**
- El `UserCreatedConsumer` recibe el evento
- Se crea un registro de usuario en la base de datos de notification-service
- El usuario queda disponible para notificaciones push

**Entidad User creada en notification-service:**
```java
User user = new User();
user.setId(userCreatedEvent.getUser_id());
user.setUsername(userCreatedEvent.getUsername());
user.setOnline(false);

userRepository.save(user);
```

**Tabla users (notification-service):**
| Campo | Tipo | Descripción |
|-------|------|-------------|
| id | VARCHAR(36) | User ID del profile-service |
| username | VARCHAR | Nombre de usuario |
| online | BOOLEAN | Estado de conexión (default: false) |

---

### Paso 8: Respuesta al cliente

El profile-service envía la respuesta de registro al cliente.

**Qué ocurre:**
- Se construye un `RegisterResponse` protobuf
- El API Gateway reenvía la respuesta al cliente
- El cliente procesa el resultado

**RegisterResponse exitosa:**
```protobuf
RegisterResponse:
  success: true
  message: "Usuario registrado exitosamente"
  userId: "uuid-generado"
```

**RegisterResponse fallida:**
```protobuf
RegisterResponse:
  success: false
  message: "Username existente"
  userId: ""
```

---

## 3. Formato de Mensajes

### RegisterRequest (Protobuf)

Definido en `register.proto`:
```protobuf
message RegisterRequest {
    string username = 1;
    string password = 2;
}
```

**Ejemplo:**
```json
{
  "username": "usuario_ejemplo",
  "password": "mi_contraseña_segura"
}
```

---

### RegisterResponse (Protobuf)

Definido en `register.proto`:
```protobuf
message RegisterResponse {
    bool success = 1;
    string message = 2;
    string userId = 3;
}
```

**Ejemplo de éxito:**
```json
{
  "success": true,
  "message": "Usuario registrado exitosamente",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Ejemplo de error:**
```json
{
  "success": false,
  "message": "Username existente",
  "userId": ""
}
```

---

### UserCreatedEvent (JSON)

Publicado en la cola `user.created`:
```json
{
  "user_id": "550e8400-e29b-41d4-a716-446655440000",
  "username": "usuario_ejemplo"
}
```

---

## 4. Estructura de Clases Principales

### profile-service

| Clase | Responsabilidad |
|-------|-----------------|
| `ProfileController` | Endpoint REST `/api/v1/auth/register` |
| `ProfileServiceImpl` | Lógica de negocio del registro |
| `CredentialsValidator` | Validación de username y password |
| `UserRepository` | Acceso a la base de datos de usuarios |
| `User` | Entidad de usuario |
| `JwtService` | Generación de tokens JWT (para login) |

### notification-service

| Clase | Responsabilidad |
|-------|-----------------|
| `UserCreatedConsumer` | Consume eventos de la cola `user.created` |
| `UserService` | Gestión de usuarios en notification-service |
| `User` | Entidad de usuario local |

### websocket-client

| Clase | Responsabilidad |
|-------|-----------------|
| `RegisterController` | Controlador del formulario de registro |
| `AuthService` | Comunicación con servicios de autenticación |
| `HttpServiceImpl` | Cliente HTTP para llamadas REST |
| `register.fxml` | Diseño del formulario de registro |

### api-gateway

| Clase | Responsabilidad |
|-------|-----------------|
| `AuthenticationFilter` | Filtro JWT para validar tokens |
| `RouteValidator` | Determina rutas públicas sin autenticación |

---

## 5. Diagrama del Flujo

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           REGISTRO DE USUARIO                                │
└─────────────────────────────────────────────────────────────────────────────┘

[Cliente JavaFX]
       │
       │ 1. Usuario llena formulario (username, password)
       │ 2. Validación cliente-side
       ▼
[RegisterController]
       │
       │ 3. POST /api/v1/auth/register (Protobuf)
       ▼
┌──────────────────────┐
│    api-gateway       │
│    (puerto 8080)     │
│                      │
│ AuthenticationFilter │
│ Valida ruta pública  │
└──────────────────────┘
       │
       │ 4. Reenvía a profile-service
       ▼
┌──────────────────────┐
│  profile-service     │
│  (puerto 8088)       │
│                      │
│ ProfileController    │
│        ↓             │
│ ProfileServiceImpl   │
│        ↓             │
│ CredentialsValidator │
└──────────────────────┘
       │
       ├──────────────────────────────┐
       │                              │
       ▼                              ▼
┌──────────────────┐        ┌──────────────────┐
│ Validación       │        │ UserRepository    │
│ fallida?         │        │                  │
└──────────────────┘        │ 5. BCrypt.hashpw │
       │                     │ 6. save(user)    │
       │                     └──────────────────┘
       ▼                              │
       │                              ▼
       │                     ┌──────────────────┐
       │                     │ RabbitMQ         │
       │                     │ user.exchange    │
       │                     │ user.created     │
       │                     └──────────────────┘
       │                              │
       │                              ▼
       │                     ┌──────────────────────────┐
       │                     │ notification-service     │
       │                     │ (puerto 8084)            │
       │                     │                          │
       │                     │ UserCreatedConsumer      │
       │                     │        ↓                 │
       │                     │ UserService.create       │
       │                     └──────────────────────────┘
       │
       ▼
┌──────────────────────────────┐
│ RegisterResponse             │
│ (Protobuf)                  │
│                              │
│ success: true/false          │
│ message: "..."               │
│ userId: "..."               │
└──────────────────────────────┘
       │
       ▼
[Cliente JavaFX]
       │
       │ 7. Mostrar resultado
       │ 8. Navegar a login si éxito
       ▼
[Login]
```

---

## 6. Manejo de Errores

### Errores del Servidor

| Error | HTTP Status | Causa | Mensaje |
|-------|-------------|-------|----------|
| Validación fallida | 400 | Username/password no cumple reglas | Detalle de validación |
| Username existente | 409 | Username ya registrado | "Username existente" |
| Error interno | 500 | Fallo en base de datos | "Error interno al registrar usuario" |

### Errores del Cliente

| Validación | Condición | Mensaje |
|------------|-----------|----------|
| Username vacío | `username.isEmpty()` | "Por favor ingrese un nombre de usuario" |
| Username corto | `username.length() < 3` | "El nombre de usuario debe tener al menos 3 caracteres" |
| Password vacío | `password.isEmpty()` | "Por favor ingrese una contraseña" |
| Password corto | `password.length() < 6` | "La contraseña debe tener al menos 6 caracteres" |
| Passwords distintos | `!password.equals(confirmPassword)` | "Las contraseñas no coinciden" |

---

## 7. Resumen

El flujo de registro de usuario garantiza que:

1. **Validación completa**: Tanto el cliente como el servidor validan los datos de entrada
2. **Seguridad en contraseñas**: Las contraseñas se hashean con BCrypt antes de almacenarse
3. **Sincronización entre servicios**: RabbitMQ asegura que notification-service tenga copia del usuario
4. **Comunicación eficiente**: REST API con protobuf para serialización eficiente
5. **Rutas públicas**: El API Gateway permite registro sin token JWT
6. **Feedback al usuario**: Mensajes claros de éxito o error en cada paso