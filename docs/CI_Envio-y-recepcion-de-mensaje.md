# Contrato de Interfaz: Envío y Recepción de Mensajes

## Descripción General

Este documento describe el flujo de comunicación para el envío y recepción de mensajes de texto en el sistema de mensajería. Los mensajes se envían en tiempo real a través de WebSocket.

---

## Mensajes Enviados por el Cliente

### Mensaje de Chat (`ChatMessage`)
- **Campo**: `id` (string)  
  **Descripción**: Identificador único del mensaje.  
  **Obligatorio**: Sí.
- **Campo**: `type` (MessageType)  
  **Descripción**: Tipo de mensaje.  
  **Obligatorio**: Sí.
- **Campo**: `sender` (string)  
  **Descripción**: Nombre del usuario que envía el mensaje.  
  **Obligatorio**: Sí.
- **Campo**: `recipient` (string)  
  **Descripción**: Nombre del usuario que recibirá el mensaje.  
  **Obligatorio**: Sí.
- **Campo**: `content` (string)  
  **Descripción**: Contenido del mensaje de texto.  
  **Obligatorio**: Sí.
- **Campo**: `timestamp` (int64)  
  **Descripción**: Timestamp de cuando se creó el mensaje.  
  **Obligatorio**: Sí.

### Solicitud de Eliminación de Mensaje (`DeleteMessageRequest`)
- **Campo**: `message_id` (string)  
  **Descripción**: Identificador del mensaje a eliminar.  
  **Obligatorio**: Sí.
- **Campo**: `sender_username` (string)  
  **Descripción**: Nombre del usuario que envía la solicitud (para verificar permisos).  
  **Obligatorio**: Sí.
- **Campo**: `recipient` (string)  
  **Descripción**: Nombre del usuario que recibió el mensaje original.  
  **Obligatorio**: Sí.

### Solicitud de Limpieza de Historial (`ClearHistoryRequest`)
- **Campo**: `sender` (string)  
  **Descripción**: Nombre del usuario que solicita la limpieza.  
  **Obligatorio**: Sí.
- **Campo**: `recipient` (string)  
  **Descripción**: Nombre del otro usuario en la conversación.  
  **Obligatorio**: Sí.

---

## Mensajes Recibidos por el Cliente

### Respuesta de Chat (`ChatMessageResponse`)
- **Campo**: `message_id` (string)  
  **Descripción**: Identificador del mensaje.
- **Campo**: `success` (bool)  
  **Descripción**: Indica si el mensaje fue enviado exitosamente.
- **Campo**: `cause` (FailureCause)  
  **Descripción**: Causa del fallo si no se pudo enviar.
- **Campo**: `error_message` (string)  
  **Descripción**: Mensaje de error descriptivo.
- **Campo**: `recipient` (string)  
  **Descripción**: Destinatario del mensaje.

#### Enum: MessageType
| Valor | Descripción |
|-------|----------|
| TEXT | Mensaje de texto |
| LOGIN | Mensaje de login |
| LOGOUT | Mensaje de logout |
| USER_LIST | Lista de usuarios |
| TYPING | Indicador de escritura |
| DELIVERY_RECEIPT | Acuse de recibo |
| ALERT | Alerta |
| CHAT | Mensaje de chat |

#### Enum: FailureCause
| Valor | Descripción |
|-------|----------|
| UNKNOWN_CAUSE | Causa desconocida |
| BLOCKED | Usuario bloqueado |

### Respuesta de Eliminación (`DeleteMessageResponse`)
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la eliminación fue exitosa.
- **Campo**: `message` (string)  
  **Descripción**: Mensaje descriptivo.
- **Campo**: `message_id` (string)  
  **Descripción**: Identificador del mensaje eliminado.

### Notificación de Mensaje Eliminado (`MessageDeletedNotification`)
- **Campo**: `message_id` (string)  
  **Descripción**: Identificador del mensaje eliminado.
- **Campo**: `deleted_by` (string)  
  **Descripción**: Nombre del usuario que eliminó el mensaje.

### Lista de Mensajes Pendientes (`UnreadMessagesList`)
- **Campo**: `messages` (ChatMessage[])  
  **Descripción**: Lista de mensajes enviados mientras el usuario estaba offline.

---

## Medio de Comunicación

- **Protocolo**: WebSocket
- **Formato de Mensajes**: Protobuf (`WsMessage`)

---

## Estructura del Mensaje WebSocket

```json
{
  "chatMessage": {
    "id": "msg-uuid",
    "type": "CHAT",
    "sender": "juan",
    "recipient": "maria",
    "content": "Hola Maria!",
    "timestamp": 1700000000
  }
}
```

```json
{
  "chatMessageResponse": {
    "message_id": "msg-uuid",
    "success": true,
    "recipient": "maria"
  }
}
```

---

## Flujo

### Flujo de Envío de Mensaje

El usuario redacta y envía un mensaje. El cliente crea un ChatMessage con ID único, sender, recipient, content y timestamp. El mensaje se envía al servidor vía WebSocket. El servidor valida que el remitente no esté bloqueado por el destinatario. Si el destinatario está conectado, el mensaje se entrega en tiempo real. Si está desconectado, el mensaje se guarda en la base de datos. El servidor envía un ChatMessageResponse al remitente confirmando el resultado.

### Flujo de Recepción de Mensajes Pendientes

Cuando el usuario se conecta, el servidor envía cualquier mensaje pendiente guardado en la base de datos a través de UnreadMessagesList. El cliente muestra estos mensajes como no leídos.

### Flujo de Eliminación de Mensaje

El usuario elimina un mensaje. El cliente envía un DeleteMessageRequest al servidor. El servidor verifica permisos y elimina el mensaje de la base de datos. Notifica al destinatario mediante MessageDeletedNotification.

---

## Notas

- Los mensajes se almacenan en la base de datos del chat-service.
- Connection-service maneja el enrutamiento en tiempo real.
- Si el destinatario está bloqueado, se retorna un ChatMessageResponse con cause = BLOCKED.