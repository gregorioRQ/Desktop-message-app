# Contrato de Interfaz: Registro de Usuario

## Descripción General

Este documento describe el flujo de registro de nuevos usuarios en el sistema de mensajería. El registro permite a los usuarios creando una cuenta con nombre de usuario y contraseña para acceder a la aplicación.

---

## Request

### Solicitud de Registro (`RegisterRequest`)
- **Campo**: `username` (string)  
  **Descripción**: Nombre del usuario a registrar. Debe ser único en el sistema.  
  **Obligatorio**: Sí.
- **Campo**: `password` (string)  
  **Descripción**: Contraseña del usuario. Se almacena encriptada.  
  **Obligatorio**: Sí.

---

## Response

### Respuesta de Registro (`RegisterResponse`)
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la operación de registro fue exitosa.  
  **Valores posibles**: `true` o `false`.
- **Campo**: `message` (string)  
  **Descripción**: Mensaje descriptivo del resultado de la operación.
- **Campo**: `userId` (string)  
  **Descripción**: Identificador único del usuario registrado.

---

## Medio de Comunicación

- **Protocolo**: HTTP
- **Formato de Mensajes**: Protobuf (`application/x-protobuf`)

---

## Endpoint

- **Método**: POST
- **URL**: `/api/v1/auth/register`
- **Content-Type**: `application/x-protobuf`

---

## Flujo

El usuario ingresa sus credenciales (username y password) en la aplicación de escritorio. El cliente envía la solicitud de registro al profile-service. El servidor valida que el username no exista y que las credenciales cumpla con los requisitos mínimos. Si es válido, el servidor encripta la contraseña y guarda el usuario en la base de datos. Retorna la respuesta con el userId asignado.

---

## Estados de Respuesta

| success | message | userId | Descripción |
|---------|----------|-------|-------------|
| true | "Usuario registrado exitosamente" | (generado) | Registro exitoso |
| false | (mensaje de validación) | "" | Error de validación (username ya existe, contraseña muy corta, etc.) |