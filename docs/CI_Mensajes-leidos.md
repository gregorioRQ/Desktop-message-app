# Contrato de Interfaz: Mensajes Leídos

## Descripción General

Este documento describe el flujo de notificación de mensajes leídos. Cuando un usuario abre un chat y visualiza los mensajes recibidos, el sistema notifica al remitente original que sus mensajes fueron leídos.

---

## Mensajes Enviados por el Cliente

### Solicitud de Marcar Mensajes como Leídos (`MarkMessagesAsReadRequest`)
- **Campo**: `sender` (string)  
  **Descripción**: Nombre del usuario que envió los mensajes ( remitente original ).  
  **Obligatorio**: Sí.
- **Campo**: `recipient` (string)  
  **Descripción**: Nombre del usuario que está leyendo los mensajes.  
  **Obligatorio**: Sí.
- **Campo**: `message_ids` (string[])  
  **Descripción**: Lista de IDs de mensajes que fueron leídos.  
  **Obligatorio**: Sí.

---

## Mensajes Recibidos por el Cliente

### Actualización de Mensajes Leídos (`MessagesReadUpdate`)
- **Campo**: `message_ids` (string[])  
  **Descripción**: Lista de IDs de mensajes que fueron leídos.
- **Campo**: `reader_username` (string)  
  **Descripción**: Nombre del usuario que leyó los mensajes.

---

## Medio de Comunicación

- **Protocolo**: WebSocket
- **Formato de Mensajes**: Protobuf (`WsMessage`)

---

## Estructura del Mensaje WebSocket

```json
{
  "markMessagesAsReadRequest": {
    "sender": "juan",
    "recipient": "maria",
    "message_ids": ["msg-1", "msg-2", "msg-3"]
  }
}
```

```json
{
  "messagesReadUpdate": {
    "message_ids": ["msg-1", "msg-2", "msg-3"],
    "reader_username": "maria"
  }
}
```

---

## Flujo

El usuario abre un chat con otro usuario en la aplicación de escritorio. El cliente envía un MarkMessagesAsReadRequest al servidor con los IDs de los mensajes leídos. El servidor actualiza el estado de los mensajes en la base de datos (marca como leídos) y envía una notificación MessagesReadUpdate al remitente original via WebSocket. El cliente del remitente recibe la actualización y muestra indicadores visuales en los mensajes correspondientes (ej: doble check azul).

---

## Notas

- Los mensajes se marcan como leídos automáticamente cuando el usuario abre el chat.
- La notificación se envía en tiempo real al remitente original.
- Si el remitente está offline, la notificación se guarda y se entrega cuando se conecte.