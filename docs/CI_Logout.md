# Contrato de Interfaz: Logout

## Descripción General

Este documento describe el flujo de cierre de sesión en el sistema de mensajería. El logout permite a los usuarios cerrar su sesión activa, invalidando el refreshToken y limpiando el estado del usuario en el sistema, incluyendo la eliminación de la clave Redis que mapea username a userId.

---

## Request

### Solicitud de Logout (`LogoutRequest`)
- **Campo**: `refreshToken` (string)  
  **Descripción**: Token de renovación utilizado para validar la sesión.  
  **Obligatorio**: Sí.
- **Campo**: `username` (string)  
  **Descripción**: Nombre del usuario que cierra sesión. Necesario para identificar qué clave Redis eliminar.  
  **Obligatorio**: Sí.

---

## Response

### Respuesta de Logout (`LogoutResponse`)
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la operación de logout fue exitosa.  
  **Valores posibles**: `true` o `false`.
- **Campo**: `message` (string)  
  **Descripción**: Mensaje descriptivo del resultado de la operación.
- **Campo**: `redisKeyDeleted` (bool)  
  **Descripción**: Indica si la clave Redis `user:name:{username}` fue eliminada correctamente.  
  **Valores posibles**: `true` o `false`.

---

## Medio de Comunicación

- **Protocolo**: HTTP
- **Formato de Mensajes**: Protobuf (`application/x-protobuf`)

---

## Endpoint

- **Método**: POST
- **URL**: `/api/v1/auth/logout`
- **Content-Type**: `application/x-protobuf`

---

## Flujo

El usuario hace clic en "Cerrar sesión" en la aplicación de escritorio. El cliente recupera el refreshToken y username de la sesión local y envía la solicitud al profile-service. El servidor elimina el refreshToken de la base de datos y publica un evento a RabbitMQ. El connection-service consume el evento y elimina la clave Redis `user:name:{username}`. El cliente recibe la respuesta y limpia la sesión local.

Para más detalles del flujo técnico, consultar el documento `logout-flow.md`.

---

## Estados de Respuesta

| success | message | redisKeyDeleted | Descripción |
|---------|----------|----------------|-------------|
| true | "Logout exitoso" | true/false | Logout exitoso |
| false | "Token no enviado" | false | refreshToken vacío |
| false | "Username no enviado" | false | username vacío |
| false | "Error durante el logout" | false | Error interno |