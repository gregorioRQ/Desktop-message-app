# Flujo: Presencia de Contactos

## Resumen

Este documento describe el proceso mediante el cual los usuarios pueden ver el estado online/offline de sus contactos.

## Arquitectura del Patrón Event Bus

### Componentes del Patrón

#### 1. Publicadores (Publishers)

**SseNotificationClient**
- Recibe eventos SSE del notification-service
- Crea objeto SseEvent y publica: `SseEventBus.publish(event)`

#### 2. Suscriptores (Subscribers) - Handlers

| Handler | Evento Suscripto | Responsabilidad |
|---------|------------------|-----------------|
| `ContactListEventHandler` | `CONTACT_LIST` | Recibe lista inicial de contactos online |
| `PresenceEventHandler` | `PRESENCE` | Procesa ONLINE/OFFLINE |
| `HeartbeatEventHandler` | `HEARTBEAT` | Maneja keepalive |
| `NotificationEventHandler` | `NOTIFICATION`, `MESSAGE` | Notificaciones system tray |

#### 3. Mediador (Event Bus)

**SseEventBus (Singleton)**
- Recibe eventos de publicadores
- Enruta a handlers correspondientes
- Ejecuta en thread JavaFX

### Diagrama del Patrón Event Bus

```
Servidor SSE
    │
    ▼
SseNotificationClient (Publisher)
    │
    ▼
SseEventBus (Mediator)
    │
    ├───────────────┬───────────────┬───────────────┐
    ▼               ▼               ▼               ▼
ContactList    PresenceEvent   HeartbeatEvent  NotificationEvent
Handler         Handler          Handler         Handler
```

## Flujo Principal

### 1. Usuario se Conecta

```
CLIENTE                          NOTIFICATION-SERVICE
  │                                       │
  │  1. Establece SSE                     │
  ├──────────────────────────────────────►│
  │                                       │ 2. Marca ONLINE
  │     event: contact_list               │ 3. Envía lista
  │◄──────────────────────────────────────┤
  │                                       │
  │  4. ContactListEventHandler           │
  │     - Resetea todos a offline         │
  │     - Marca contactos online          │
```

### 2. Contacto se Conecta/Desconecta

```
CLIENTE B                     NOTIFICATION-SERVICE
  │                                    │
  │ 1. Establece/Cierra SSE            │
  ├─────────────────────────────────►│
  │                                    │ 2. Detecta cambio
  │     event: presence                │ 3. Notifica contactos
  │◄───────────────────────────────────┤
  │                                    │
  │ 4. PresenceEventHandler            │
  │    - Actualiza estado            │
```

## Ventajas del Diseño

1. **Desacoplamiento:** Publicadores no conocen handlers
2. **Extensibilidad:** Fácil agregar nuevos tipos de eventos
3. **Testabilidad:** Cada handler puede probarse en aislamiento
4. **Robustez:** Un handler fallido no afecta a los demás

