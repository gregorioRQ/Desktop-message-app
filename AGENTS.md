# AGENTS.md - Agent Configuration for MSG Desktop Project

This document provides essential information for AI coding agents working on this multi-module Java messaging application.

## Project Overview

This is a Java/JavaFX messaging application with Spring Boot microservices backend:
- **websocket-client**: JavaFX desktop client
- **profile-service**: Authentication and user management (port 8088)
- **connection-service**: WebSocket connections and message routing (port 8083)
- **chat-service**: Real-time messaging (port 8085)
- **notification-service**: Notifications and presence (port 8084)
- **api-gateway**: API gateway
- **media-service**: Media handling

## Build, Lint, and Test Commands

### Maven Commands (all modules)
```bash
# Clean and compile project
mvn clean compile

# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ClassNameTest

# Run specific test method
mvn test -Dtest=ClassNameTest#methodName

# Package (creates JAR)
mvn package

# Package skipping tests
mvn package -DskipTests

# Run Spring Boot application
mvn spring-boot:run

# Compile Protobuf files
mvn protobuf:compile
```

### Module-Specific Ports
- profile-service: 8088
- connection-service: 8083
- chat-service: 8085
- notification-service: 8084

### Docker
```bash
docker-compose up --build
```

## Code Style Guidelines

### Imports
Order imports as follows:
1. Java standard library (`java.*`, `javax.*`)
2. Third-party libraries (Spring, Protobuf, Lombok)
3. Project-specific imports (`com.basic_chat.*`, `com.pola.*`)

Use static imports at the top, separated by blank line. Avoid wildcards except in test classes.

### Formatting
- Indentation: 4 spaces (no tabs)
- Line length: 120 characters max
- Curly braces: Opening on same line, closing on new line
- Empty lines: One between methods, two between classes

### Naming Conventions
| Type | Convention | Examples |
|------|------------|----------|
| Classes | PascalCase | `UserService`, `ChatController` |
| Methods | camelCase | `sendMessage`, `loadChatHistory` |
| Variables | camelCase | `userName`, `webSocketService` |
| Constants | UPPER_SNAKE_CASE | `MAX_RETRIES`, `DEFAULT_TIMEOUT` |
| Packages | lowercase | `com.basic_chat.chat_service` |

Suffixes: Service, Repository, Controller, Handler, Config, DTO

### Types
- Use Java 21 features where appropriate
- Prefer primitives over wrapper classes when nullable not needed
- Favor interfaces over implementations:
  ```java
  List<String> messages = new ArrayList<>(); // Good
  ```

### Error Handling
1. Log all exceptions with meaningful context (user IDs, message IDs, etc.)
2. Use specific exception types over generic Exception
3. Return appropriate HTTP status codes:
   - 201 Created: Successful creation
   - 400 Bad Request: Validation failures
   - 401 Unauthorized: Authentication failures
   - 500 Internal Server Error: Unexpected errors
4. Don't expose internal error details to clients
5. Fail fast - validate inputs early

### Logging
Use SLF4J with Lombok @Slf4j. Log levels:
- INFO: Application flow and successful operations
- WARN: Potential issues
- ERROR: Exceptions and failures

Use parameterized logging: `log.info("User: {}", username)`

### Testing Patterns
1. Use JUnit 5 with Mockito
2. Follow AAA pattern: Arrange, Act, Assert
3. Use @DisplayName for descriptive test names
4. Use @Mock, @InjectMocks with @ExtendWith(MockitoExtension.class)
5. Verify interactions with mocks using verify()

### Spring Best Practices
1. Use constructor injection (@RequiredArgsConstructor)
2. Mark services with @Service, repositories with @Repository
3. Use @Transactional for database operations
4. Configure properties in application.properties

### Protobuf Guidelines
1. Define messages in `src/main/proto/*.proto` files
2. Generated classes in `src/generated/java/`
3. Use builder pattern: `Message.newBuilder()`
4. Don't modify generated classes
5. Content-Type: application/x-protobuf

### Handler Pattern (connection-service and chat-service)
Both services use a handler pattern for processing different types of WebSocket/RabbitMQ messages. This pattern allows adding new message types without modifying existing code.

**connection-service Handlers:**
- `ConnectionWsMessageHandler` - Interface defining `supports(message)` and `handle(sender, message)`
- `ChatMessageHandler` - Processes ChatMessage
- `DeleteMessageHandler` - Processes DeleteMessageRequest
- `ClearHistoryHandler` - Processes ClearHistoryRequest

**chat-service Handlers:**
- `WsMessageHandler` - Interface for processing messages
- Implementations: ChatMessageHandler, DeleteMessageHandler, ClearHistoryHandler, BlockContactHandler, UnblockContactHandler, MarkAsReadHandler, ContactIdentityHandler

**Dispatcher Pattern:**
```java
// ConnectionMessageDispatcher (connection-service)
for (WsMessageHandler handler : handlers) {
    if (handler.supports(message)) {
        handler.handle(context, message);
        return;
    }
}
```

### JavaFX Specific (websocket-client)
1. UI updates must run on JavaFX Application Thread:
   ```java
   Platform.runLater(() -> { /* UI updates */ });
   ```
2. Use properties and bindings for reactive UI
3. Follow MVC pattern (controllers, services, models separated)
4. Use FXML for UI layout when possible

### Security
1. Passwords stored with BCrypt
2. JWT tokens for authentication (access/refresh pattern)
3. Validate all inputs before processing
4. Don't log sensitive information (passwords, tokens)

## Project Structure

```
src/
├── main/
│   ├── java/com/basic_chat/[service]/
│   │   ├── controller/   # REST endpoints
│   │   ├── service/      # Business logic
│   │   ├── repository/   # Data access
│   │   ├── models/       # Entities
│   │   ├── config/       # Configuration
│   │   └── exception/    # Custom exceptions
│   ├── proto/           # Protobuf definitions
│   ├── resources/       # application.properties
│   └── generated/java/  # Generated Protobuf classes
└── test/java/           # Unit tests
```

## Code Review Checklist

- [ ] All tests pass locally
- [ ] New functionality covered by unit tests
- [ ] Proper exception handling implemented
- [ ] Appropriate logging added
- [ ] Code follows style guidelines
- [ ] No hardcoded values (use configuration)
- [ ] Protobuf messages built with .newBuilder()

## Common Issues
1. NullPointerException with Protobuf optional fields
2. Resource leaks in database operations
3. Improper transaction boundaries
4. Missing validation on inputs
5. JavaFX threading violations
6. **Multi-instance routing**: When connection-service sends messages, always use RabbitMQ queues - never try direct WebSocket (client may be connected to different instance)
7. **Handler dispatch**: connection-service uses ConnectionMessageDispatcher - add new handlers by implementing ConnectionWsMessageHandler interface
