# Contrato de interfaz: Registro de usuario

## Mensajes

### **Datos Enviados por el Cliente**

#### **Solicitud de registro (`RegisterRequest`)**
- **Campo**: `username` (string)  
  **Descripción**: Nombre del usuario a registrar.  
  **Obligatorio**: Sí.
- **Campo**: `password` (string)
  **Descripción**: Contraseña del usuario.
  **Obligatorio**: Si.

---

### **Datos Recibidos por el Cliente**

#### **Respuesta de Registro (`RegisterResponse`)**
- **Campo**: `success` (bool)  
  **Descripción**: Indica si la operación de registro fue exitosa.  
  **Valores posibles**: `true` o `false`.
- **Campo**: `message` (string)
  **Descripción**: Mensaje descriptivo del resultado de la operación.
- **Campo**: `userId` (string)
  **Descripción**: el Id del usuario que realizó la operación.

---

## Protocolos y Medios

### **Medio de Comunicación**
- **Protocolo**: WebSocket  
- **Formato de Mensajes**: Protobuf (`WsMessage`)

### **Estructura del Mensaje WebSocket**
Todos los mensajes enviados y recibidos están encapsulados en un mensaje de tipo `WsMessage`.

---

## ***Flujos de Comunicación***

**Flujo de Registro de usuario**

El usuario envia sus credenciales de registro.
El servidor valida si no existe en el sistema.
Registra al usuario y retorna la respuesta.
