# Flujo de Logout

## Propósito

Este documento describe el flujo completo de cierre de sesión (logout) en la aplicación de mensajería. 
Explica cómo el sistema limpia el estado del usuario al cerrar sesión, incluyendo la eliminación de la 
clave Redis que mapea username a userId.

Este documento sirve como referencia técnica para desarrolladores que necesiten entender, mantener 
o modificar este flujo en el futuro.

## Diagrama del flujo

```
┌──────────────┐    ┌─────────────────┐    ┌────────────┐    ┌──────────────────┐    ┌───────┐
│   Cliente    │───>│profile-service  │───>│  RabbitMQ │───>│connection-svc   │───>│ Redis │
│  Desktop     │    │                 │    │           │    │                 │    │       │
└──────────────┘    └─────────────────┘    └────────────┘    └──────────────────┘    └───────┘
```

## Descripción del flujo

### 1. Cliente Desktop

El usuario hace clic en el botón "Cerrar sesión" en la aplicación de escritorio. 
El cliente recupera el username y refreshToken de la sesión local y envía una solicitud 
de logout al profile-service.

**Responsable de**: Enviar la solicitud de logout con las credenciales del usuario.

### 2. profile-service

Recibe la solicitud de logout con el refreshToken y username. Valida que ambos parámetros 
no estén vacíos. Elimina el refreshToken de la base de datos y publica un evento a RabbitMQ 
con el username del usuario que cerró sesión.

**Responsable de**: 
- Validar la solicitud de logout
- Eliminar el refreshToken de la base de datos
- Publicar el evento de logout a RabbitMQ

### 3. RabbitMQ (Cola: user.logout)

Recibe el evento publicado por profile-service y lo enruta al connection-service. 
RabbitMQ actúa como intermediario asíncrono entre los servicios, desacoplando la comunicación.

**Responsable de**: 
- Recibir y almacenar el evento temporalmente
- Entregar el evento al connection-service

### 4. connection-service

Consume el evento de la cola `user.logout`. Recibe el username y elimina la clave 
Redis `user:name:{username}` que mapeaba el nombre de usuario a su ID único.

**Responsable de**: 
- Escuchar la cola de eventos de logout
- Eliminar la clave Redis asociada al usuario

### 5. Redis

Almacenaba la clave `user:name:{username}` que ahora ha sido eliminada, limpiando 
el estado del usuario del sistema. Esta clave servía para mapear el nombre de usuario 
a su ID único en el sistema.

**Responsable de**: 
- Almacenar temporalmente el mapeo username -> userId
- Eliminar la clave cuando se solicita

## Detalles técnicos

| Componente | Valor |
|-----------|-------|
| Cola RabbitMQ | `user.logout` |
| Exchange | `user.exchange` |
| Routing key | `user.logout` |
| Clave Redis eliminada | `user:name:{username}` |
| Puerto profile-service | 8088 |
| Puerto connection-service | 8083 |

## Código relevante

### profile-service

- **Configuración**: `UserLogoutRabbitConfig.java`
- **Servicio**: `ProfileServiceImpl.logout()`
- **Propiedades**: `application.properties` (configuración RabbitMQ)

### connection-service

- **Configuración**: `UserLogoutRabbitConfig.java`
- **Consumer**: `UserLogoutConsumer.java`
- **Constante**: `USER_NAME_PREFIX = "user:name:"` en `SessionRegistryService.java`

## Consideraciones

- **Desacoplamiento**: RabbitMQ permite que profile-service y connection-service no dependan 
  directamente el uno del otro, evitando acoplamiento por URL.
- **Asincronía**: El flujo es asíncrono, el cliente no espera a que se elimine la clave Redis.
- **Tolerancia a fallos**: Si connection-service no está disponible, el evento queda en cola 
  hasta que sea procesado.