# Contrato de Interfaz: Eliminación de mensajes y borrado de historial

## Mensajes

### **Datos enviados por el cliente**

#### **Eliminación de un mensaje (`DeleteMessageRequest`)**
- **Campo**: `message_id` (string)
  **Descripción**: Identificador del mensaje.  
  **Obligatorio**: Sí.
- **Campo**: `sender_username` (string)
  **Descripción**: Username del propietario del mensaje.  
  **Obligatorio**: Sí.

#### **Solicitud de borrado de historial (`ClearHistoryRequest`)
- **Campo**: `sender` (string)
  **Descripción**: El username del usuario.
- **Campo**: `recipient` (string)
  **Descripción**: El username del receptor.

---

### **Datos recibidos por el cliente**

#### **Respuesta a eliminación de mensaje (`DeleteMessageResponse`)
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la operación de bloqueo fue exitosa.  
  **Valores posibles**: `true` o `false`.
- **Campo**: `message` (string)  
  **Descripción**: Mensaje descriptivo del resultado de la operación.
- **Campo**: `message_id` (string)
  **Descripción**: Identificador del mensaje.

---

## Protocolos y Medios

### **Medio de Comunicación**
- **Protocolo**: WebSocket  
- **Formato de Mensajes**: Protobuf (`WsMessage`)

### **Estructura del Mensaje WebSocket**
Todos los mensajes enviados y recibidos están encapsulados en un mensaje de tipo `WsMessage`.

---

## ***Flujos de Comunicación***

Solicitud del cliente:
- Elimina el mensaje en su db local.
- Prepara la solicitud para ser enviada al servidor.

Procesamiento en el servidor:
- Verifica si el receptor esta online.
si esta entrega directamente la request sino lo guarda en la db.

Cliente del receptor:
- Recibe el mensaje.
- Procesa la eliminación en su db local.
