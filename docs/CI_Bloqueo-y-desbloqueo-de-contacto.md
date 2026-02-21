# Contrato de Interfaz: Bloqueo y Desbloqueo de Contactos

## Descripción General

Este documento describe el flujo de comunicación entre el cliente y el servidor para las operaciones de bloqueo y desbloqueo de contactos en el sistema de mensajería. Estas operaciones permiten a los usuarios gestionar su lista de contactos bloqueados, asegurando que no puedan recibir mensajes de usuarios bloqueados.

---

## Mensajes

### **Datos Enviados por el Cliente**

#### **Bloqueo de Contacto (`BlockContactRequest`)**
- **Campo**: `recipient` (string)  
  **Descripción**: Nombre del usuario que se desea bloquear.  
  **Obligatorio**: Sí.

#### **Desbloqueo de Contacto (`UnblockContactRequest`)**
- **Campo**: `recipient` (string)  
  **Descripción**: Nombre del usuario que se desea desbloquear.  
  **Obligatorio**: Sí.

---

### **Datos Recibidos por el Cliente**

#### **Respuesta de Bloqueo (`BlockContactResponse`)**
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la operación de bloqueo fue exitosa.  
  **Valores posibles**: `true` o `false`.

- **Campo**: `message` (string)  
  **Descripción**: Mensaje descriptivo del resultado de la operación.

#### **Respuesta de Desbloqueo (`UnblockContactResponse`)**
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la operación de desbloqueo fue exitosa.  
  **Valores posibles**: `true` o `false`.

- **Campo**: `message` (string)  
  **Descripción**: Mensaje descriptivo del resultado de la operación.

---

## Protocolos y Medios

### **Medio de Comunicación**
- **Protocolo**: WebSocket  
- **Formato de Mensajes**: Protobuf (`WsMessage`)

### **Estructura del Mensaje WebSocket**
Todos los mensajes enviados y recibidos están encapsulados en un mensaje de tipo `WsMessage`.

#### **Ejemplo de Mensaje de Bloqueo**
```json
{
  "blockContactRequest": {
    "recipient": "user_to_block"
  }
}
```
#### **Ejemplo de respuesta de Bloqueo**
```json
{
  "blockContactResponse": {
    "success": true,
    "message": "Usuario bloqueado exitosamente"
  }
}
```

#### **Ejemplo de Mensaje de Desbloqueo**
```json
{
  "unblockContactRequest": {
    "recipient": "user_to_unblock"
  }
}
```
#### **Ejemplo de respuesta de Desbloqueo**
```json
{
  "unblockContactResponse": {
    "success": true,
    "message": "Usuario desbloqueado exitosamente"
  }
}
```
---

## ***Flujos de Comunicación***

**Flujo de Bloqueo de Contacto**

Solicitud del Cliente:

El cliente envía un mensaje BlockContactRequest con el nombre del usuario a bloquear (recipient).
Validación del Servidor:

El servidor valida que:
El usuario autenticado tiene una sesión activa.
El usuario a bloquear existe en el sistema.
Ejecución del Bloqueo:

Si el usuario a bloquear está conectado:
Se envía una notificación en tiempo real al usuario bloqueado.
Si el usuario está desconectado:
Se guarda una notificación pendiente en la base de datos (PendingBlock).
Respuesta del Servidor:

El servidor envía un mensaje BlockContactResponse al cliente indicando el resultado de la operación.

**Flujo de Desbloqueo de Contacto**

Solicitud del Cliente:

El cliente envía un mensaje UnblockContactRequest con el nombre del usuario a desbloquear (recipient).
Validación del Servidor:

El servidor valida que:
El usuario autenticado tiene una sesión activa.
El usuario a desbloquear existe en el sistema.
Ejecución del Desbloqueo:

Si el usuario estaba bloqueado, se elimina el registro de bloqueo.
Si había notificaciones pendientes, se eliminan (PendingBlock).
Respuesta del Servidor:

El servidor envía un mensaje UnblockContactResponse al cliente indicando el resultado de la operación.


