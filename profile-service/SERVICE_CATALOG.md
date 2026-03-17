# Service Catalog: Profile Service

## 1. Información General

- **Nombre**: Profile Service
- **Puerto**: 8088
- **Descripción**: Servicio de gestión de usuarios y autenticación del sistema de mensajería
- **Tecnología**: Spring Boot 3.2.0, Java 21, Protobuf, MySQL, JWT

---

## 2. Problemas que Resuelve

1. **Registro de usuarios**: Valida y almacena credenciales de nuevos usuarios en el sistema
2. **Autenticación**: Verifica identidad de usuarios mediante usuario y contraseña
3. **Gestión de tokens**: Genera y administra tokens JWT para sesiones seguras
4. **Persistencia de sesiones**: Maneja refresh tokens para mantener sesiones largas
5. **Cierre de sesiones**: Permite logout invalidando refresh tokens

---

## 3. Procesos en los que Participa

### 3.1 Registro de Usuario

1. Cliente envía credenciales (username, password)
2. Valida formato y longitud del username
3. Valida que el username no exista previamente
4. Valida longitud mínima de la contraseña
5. Encripta la contraseña con BCrypt
6. Persiste el usuario en MySQL
7. Retorna userId generado

### 3.2 Login / Autenticación

1. Cliente envía credenciales (username, password)
2. Busca usuario por username en MySQL
3. Verifica contraseña con BCrypt
4. Genera JWT access token (vigencia: 24 horas)
5. Genera refresh token (vigencia: 30 días)
6. Persiste refresh token en MySQL asociado al usuario y dispositivo
7. Retorna ambos tokens

### 3.3 Refresh de Access Token

1. Cliente envía refresh token
2. Busca token en MySQL
3. Verifica que no haya expirado
4. Obtiene usuario asociado al token
5. Genera nuevo access token
6. Retorna nuevo access token

### 3.4 Logout

1. Cliente envía refresh token
2. Elimina el refresh token de MySQL
3. Confirma eliminación exitosa

---

## 4. Flujos de Integración con Otros Servicios

### 4.1 Flujo de Autenticación Global

```
[Cliente] 
    │
    ▼ (POST /api/v1/auth/register | /login)
[API Gateway :8080]
    │
    ▼ (ruteo)
[Profile Service :8088]
    │
    ▼ (consulta MySQL)
[profile_db]
```

```
[Cliente] 
    │
    ▼ (WebSocket / JWT)
[API Gateway :8080]
    │
    ▼ (valida JWT con clave compartida)
[Chat Service :8085]
```

### 4.2 Interacciones por Servicio

| Servicio | Tipo de Comunicación | Descripción |
|----------|---------------------|-------------|
| **API Gateway** | HTTP REST (Protobuf) | Recibe requests en `/api/v1/auth/**`, rutea al profile-service |
| **Chat Service** | JWT Compartido | Valida tokens JWT generados por profile-service (usa la misma clave secreta) |
| **Media Service** | Ninguna | No existe interacción directa |
| **Notification Service** | Ninguna | No existe interacción directa |

### 4.3 Clave JWT Compartida

El profile-service genera tokens JWT usando una clave secreta que también es utilizada por:
- **API Gateway**: Para validar tokens en requests entrantes
- **Chat Service**: Para validar tokens en conexiones WebSocket

Todos deben usar la misma clave secreta para que la validación sea exitosa.

---

## 5. Endpoints

| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | /api/v1/auth/register | Registrar nuevo usuario |
| POST | /api/v1/auth/login | Iniciar sesión y obtener tokens |
| POST | /api/v1/auth/refresh | Renovar access token con refresh token |
| POST | /api/v1/auth/logout | Cerrar sesión e invalidar refresh token |

**Content-Type**: `application/x-protobuf`

---

## 6. Configuración Importante

- **Puerto**: 8088
- **Base de datos**: MySQL (profile_db)
- **JWT Secret**: Clave compartida con API Gateway y Chat Service
- **JWT Access Token**: 24 horas de vigencia
- **JWT Refresh Token**: 30 días de vigencia
- **Validadores**:
  - Username: mínimo 3 caracteres, solo letras, números, guiones y guiones bajos
  - Password: mínimo 6 caracteres

---

## 7. Formato de Respuestas

Todas las respuestas incluyen un campo `success` (boolean) y `message` (string):

- **Registro exitoso**: `success=true`, `message="Usuario registrado exitosamente"`, retorna `userId`
- **Login exitoso**: `success=true`, `message="Login exitoso"`, retorna `userId` y `tokens`
- **Error de validación**: `success=false`, `message` con descripción del error
- **Error de autenticación**: `success=false`, `message="Usuario o contraseña incorrectos"`
- **Error interno**: `success=false`, `message="Error interno..."` (sin detalles técnicos al cliente)
