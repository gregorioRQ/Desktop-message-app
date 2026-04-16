# Contrato de Interfaz: Bloqueo y Desbloqueo de Contactos

## Descripción General

Este documento describe el flujo de comunicación entre el cliente y el servidor para las operaciones de bloqueo y desbloqueo de contactos en el sistema de mensajería. Estas operaciones permiten a los usuarios gestionar su lista de contactos bloqueados, asegurando que no puedan recibir mensajes de usuarios bloqueados.

---

## Mensajes Enviados por el Cliente

### Solicitud de Bloqueo de Contacto (`BlockContactRequest`)
- **Campo**: `blocker` (string)  
  **Descripción**: Nombre del usuario que envía la solicitud de bloqueo (quien bloquea).  
  **Obligatorio**: Sí.
- **Campo**: `recipient` (string)  
  **Descripción**: Nombre del usuario que serábloqueado.  
  **Obligatorio**: Sí.

### Solicitud de Desbloqueo de Contacto (`UnblockContactRequest`)
- **Campo**: `blocker` (string)  
  **Descripción**: Nombre del usuario que envía la solicitud de desbloqueo (quien desbloquea).  
  **Obligatorio**: Sí.
- **Campo**: `recipient` (string)  
  **Descripción**: Nombre del usuario que será desbloqueado.  
  **Obligatorio**: Sí.

---

## Mensajes Recibidos por el Cliente

### Respuesta de Bloqueo de Contacto (`BlockContactResponse`)
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la operación de bloqueo fue exitosa.  
  **Valores posibles**: `true` o `false`.
- **Campo**: `message` (string)  
  **Descripción**: Mensaje descriptivo del resultado de la operación.

### Respuesta de Desbloqueo de Contacto (`UnblockContactResponse`)
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la operación de desbloqueo fue exitosa.  
  **Valores posibles**: `true` o `false`.
- **Campo**: `message` (string)  
  **Descripción**: Mensaje descriptivo del resultado de la operación.

---

## Medio de Comunicación

- **Protocolo**: WebSocket
- **Formato de Mensajes**: Protobuf (`WsMessage`)

---

## Estructura del Mensaje WebSocket

Todos los mensajes se encapsulan en un `WsMessage`:

```json
{
  "blockContactRequest": {
    "blocker": "juan",
    "recipient": "pedro"
  }
}
```

```json
{
  "blockContactResponse": {
    "success": true,
    "message": "Usuario bloqueado exitosamente"
  }
}
```

```json
{
  "unblockContactRequest": {
    "blocker": "juan",
    "recipient": "pedro"
  }
}
```

```json
{
  "unblockContactResponse": {
    "success": true,
    "message": "Usuario desbloqueado exitosamente"
  }
}
```

---

## Flujo de Comunicación

### Flujo de Bloqueo de Contacto

El cliente envía un mensaje `BlockContactRequest` con el nombre del usuario que bloquea (`blocker`) y el usuario a bloquear (`recipient`). El servidor valida que el usuario autenticado tenga una sesión activa y que el usuario a bloquear exista en el sistema. Si el usuario a bloquear está conectado, se envía una notificación en tiempo real. Si está desconectado, se guarda una notificación pendientes. El servidor envía un `BlockContactResponse` al cliente indicando el resultado.

### Flujo de Desbloqueo de Contacto

El cliente envía un mensaje `UnblockContactRequest` con el nombre del usuario que desbloquea (`blocker`) y el usuario a desbloquear (`recipient`). El servidor valida que el usuario autenticado tenga una sesión activa y que el usuario a desbloquear exista en el sistema. Si el usuario estaba bloqueado, se elimina el registro de bloqueo. Si había notificaciones pendientes, se eliminan. El servidor envía un `UnblockContactResponse` al cliente indicando el resultado.

---

## Listas de Contactos (Mensajes Adicionales)

### Lista de Usuarios Bloqueados (`BlockedUsersList`)
- **Campo**: `users` (string[])  
  **Descripción**: Lista de nombres de usuarios bloqueados por el usuario.

### Lista de Usuarios Desbloqueados (`UnblockedUsersList`)
- **Campo**: `users` (string[])  
  **Descripción**: Lista de nombres de usuarios que fueron desbloqueados (disponibles para comunicar nuevamente).