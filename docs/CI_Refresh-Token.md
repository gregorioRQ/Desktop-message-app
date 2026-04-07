# Contrato de Interfaz: Refresh Token

## Descripción General

Este documento describe el flujo de renovación de tokens de acceso. El refresh token permite obtener un nuevo accessToken cuando el actual ha expirado, sin necesidad de que el usuario vuelva a ingresar sus credenciales.

---

## Request

### Solicitud de Refresh (`RefreshRequest`)
- **Campo**: `token` (string)  
  **Descripción**: Refresh token válido utilizado para solicitar un nuevo accessToken.  
  **Obligatorio**: Sí.

---

## Response

### Respuesta de Refresh (`RefreshResponse`)
- **Campo**: `accessToken` (string)  
  **Descripción**: Nuevo token de acceso generado. Vacío si el refresh falló.
- **Campo**: `refreshToken` (string)  
  **Descripción**: Refresh token original (puede ser el mismo o renovado). Vacío si el refresh falló.
- **Campo**: `errorMsg` (string)  
  **Descripción**: Mensaje de error si el refresh falló. Vacío si fue exitoso.

---

## Medio de Comunicación

- **Protocolo**: HTTP
- **Formato de Mensajes**: Protobuf (`application/x-protobuf`)

---

## Endpoint

- **Método**: POST
- **URL**: `/api/v1/auth/refresh`
- **Content-Type**: `application/x-protobuf`

---

## Flujo

Cuando el accessToken expira, el cliente envía un RefreshRequest con el refreshToken almacenado localmente al profile-service. El servidor verifica que el refreshToken sea válido y no haya expirado. Si es válido, genera un nuevo accessToken y lo retorna al cliente. El cliente reemplaza el accessToken antiguo con el nuevo y continúa haciendo solicitudes.

---

## Estados de Respuesta

| accessToken | refreshToken | errorMsg | Descripción |
|-------------|-------------|----------|-------------|
| (nuevo token) | (token original) | "" | Refresh exitoso |
| "" | "" | "Refresh token inválido o no encontrado" | Token no válido |
| "" | "" | "El token ha expirado" | Token expirado |