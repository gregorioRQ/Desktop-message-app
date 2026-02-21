# Contrato de interfaz: Envío de mensaje

## Mensajes

### **Datos Enviados por el Cliente**

#### **Mensaje de chat (`ChatMessage`)**
- **Campo**: `id` (string)  
  **Descripción**: El identificador del mensaje.  
  **Obligatorio**: Sí.
- **Campo**: `sender` (string)
  **Descripción**: El usuario que envía al mensaje.
  **Obligatorio**: Si.
- **Campo**: `recipient` (string)
  **Descripción**: El usuario que recibirá el mensaje.
  **Obligatorio**: Si.
- **Campo**: `content` (string)
  **Descripción**: El contenido del mensaje.
  **Obligatorio**: Si.
- **Campo**: `timestamp` (integer)
  **Descripción**: La fecha de creación del mensaje en el cliente.
  **Obligatorio**: Si.

---

### **Datos Recibidos por el Cliente**

#### **Respuesta en caso de error (`ChatMessageResponse`)**
- **Campo**: `message_id` (string)  
  **Descripción**: Identificador del mensaje comprometido.  
- **Campo**: `success` (boolean)
  **Descripción**: Indica si la operación fue exitosa.
  **Valores posibles**: `true` o `false`.
- **Campo**: `cause` (FailureCause)
  **Descripción**: Demuestra porque fallo la operación.

---

### **Datos Recibidos por el Cliente**

#### **Mensajes pendiendtes (`UnreadMessagesList`)**
- **Campo**: `messages` (ChatMessage)  
  **Descripción**: Una lista con los mensajes que enviaron al receptor cuando estaba offline.  

---

## Protocolos y Medios

### **Medio de Comunicación**
- **Protocolo**: WebSocket  
- **Formato de Mensajes**: Protobuf (`WsMessage`)

### **Estructura del Mensaje WebSocket**
Todos los mensajes enviados y recibidos están encapsulados en un mensaje de tipo `WsMessage`.

---

## ***Flujos de Comunicación***

**Flujo de envío de un mensaje**

Solicitud del Cliente:

El usuario redacta y envía el mensaje.
El cliente lo guarda en su db local.
El api gateway verifica si trae un token válido.
El servicio de mensajes:
Verifica si el receptor no esta bloqueado por el remitente:
Si lo está retorna la advertencia al remitente.
Verifica si el receptor está en linea:
Consulta en redis para ver el estado (online) del receptor:
Si lo encuetra recupera su id de la sesion websocket previamente guardado por el api gateway.
Verifica si la sesion WebSocket con ese id existe:
Si existe lo entrega sino lo guarda en la db del servidor.

Cuando el cliente del remitente se conecta el servicio envia los mensajes guardados en la db.
El cliente lo guarda en su db local.

