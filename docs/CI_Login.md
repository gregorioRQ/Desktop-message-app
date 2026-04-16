# Contrato de Interfaz: Login

## Descripción General

Este documento describe el flujo de autenticación de usuarios en el sistema de mensajería. El login permite a los usuarios iniciar sesión en la aplicación mediante credenciales válidas, recibiendo un par de tokens (accessToken y refreshToken) para futuras autenticaciones.

---

## Request

### Solicitud de Login (`LoginRequest`)
- **Campo**: `username` (string)  
  **Descripción**: Nombre del usuario que desea iniciar sesión.  
  **Obligatorio**: Sí.
- **Campo**: `password` (string)  
  **Descripción**: Contraseña del usuario.  
  **Obligatorio**: Sí.
- **Campo**: `deviceId` (string)  
  **Descripción**: Identificador único del dispositivo desde el cual se inicia sesión. Se utiliza para generar refresh tokens específicos por dispositivo.  
  **Obligatorio**: Sí.

---

## Response

### Respuesta de Login (`LoginResponse`)
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la operación de login fue exitosa.  
  **Valores posibles**: `true` o `false`.
- **Campo**: `message` (string)  
  **Descripción**: Mensaje descriptivo del resultado de la operación.
- **Campo**: `userId` (string)  
  **Descripción**: Identificador único del usuario que ha iniciado sesión.
- **Campo**: `tokens` (TokenPair)  
  **Descripción**: Par de tokens de autenticación.

#### TokenPair (`TokenPair`)
- **Campo**: `accessToken` (string)  
  **Descripción**: Token de acceso para autenticar solicitudes a la API. Tiene vigencia limitada (24 horas).
- **Campo**: `refreshToken` (string)  
  **Descripción**: Token para obtener un nuevo accessToken cuando expira. Tiene vigencia de 30 días.

---

## Medio de Comunicación

- **Protocolo**: HTTP
- **Formato de Mensajes**: Protobuf (`application/x-protobuf`)

---

## Endpoint

- **Método**: POST
- **URL**: `/api/v1/auth/login`
- **Content-Type**: `application/x-protobuf`

---

## Flujo

El usuario ingresa sus credenciales (username y password) en la aplicación de escritorio. El cliente envía la solicitud de login al profile-service, que valida las credenciales contra la base de datos. Si son válidas, el servidor genera un par de tokens y los retorna al cliente junto con el userId. El cliente almacena estos tokens localmente para autenticar solicitudes posteriores.

---

## Estados de Respuesta

| success | message | Descripción |
|---------|----------|-------------|
| true | "Login exitoso" | Login exitoso, tokens generados |
| false | "Usuario o contraseña incorrectos" | Credenciales inválidas |