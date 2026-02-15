# Guía de Refactorización: Cambios de SessionManager a RedisSessionService

**Estado**: BlockContactHandler ✅, ChatMessageHandler ✅, UnblockContactHandler ✅

**Pendientes**: DeleteMessageHandler, ContactIdentityHandler, ClearHistoryHandler, MarkAsReadHandler

---

## Patrón de cambios para cada handler

### 1. Agregar import
```java
import com.basic_chat.chat_service.service.RedisSessionService;
```

### 2. Agregar al constructor
- Agregar parámetro: `RedisSessionService redisSessionService`
- Agregar asignación: `this.redisSessionService = redisSessionService;`

### 3. Reemplazar los dos métodos

**Cambio A: De `isUserOnline(username)`**
```java
// ANTES
if (sessionManager.isUserOnline(username)) {
    // hacer algo
}

// DESPUÉS
if (redisSessionService.isUserOnlineByUsername(username)) {
    // hacer algo
}
```

**Cambio B: De `findByUsername(username)`**
```java
// ANTES
SessionManager.SessionInfo session = sessionManager.findByUsername(username);
if (session != null) {
    // usar session
}

// DESPUÉS
String sessionId = redisSessionService.getSessionIdByUsername(username);
if (sessionId != null) {
    SessionManager.SessionInfo session = sessionManager.getSessionInfo(sessionId);
    if (session != null) {
        // usar session
    }
}
```

---

## Handlers por actualizar

### DeleteMessageHandler
- **Línea ~163**: `sessionManager.isUserOnline(recipient)` → `redisSessionService.isUserOnlineByUsername(recipient)`
- **Línea ~192**: `sessionManager.findByUsername(recipient)` → Aplicar patrón B arriba

### ContactIdentityHandler
- **Línea ~77**: `sessionManager.isUserOnline(recipient)` → `redisSessionService.isUserOnlineByUsername(recipient)`
- **Línea ~105**: `sessionManager.findByUsername(recipient)` → Aplicar patrón B arriba

### ClearHistoryHandler
- **Línea ~126**: `sessionManager.isUserOnline(recipient)` → `redisSessionService.isUserOnlineByUsername(recipient)`
- **Línea ~132**: `sessionManager.findByUsername(recipient)` → Aplicar patrón B arriba

### MarkAsReadHandler
- **Línea ~163**: `sessionManager.isUserOnline(sender)` → `redisSessionService.isUserOnlineByUsername(sender)`
- **Línea ~190**: `sessionManager.findByUsername(sender)` → Aplicar patrón B arriba

---

## Verificación final
Ejecutar: `mvn clean compile` para verificar que no hay errores de compilación
