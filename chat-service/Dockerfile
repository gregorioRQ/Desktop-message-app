FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copia pom.xml primero (para cachear dependencias)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copia el código fuente
COPY src ./src

# Compila el proyecto
RUN mvn clean package -DskipTests

# Imagen final optimizada
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copia el JAR compilado
COPY --from=build /app/target/*.jar app.jar

EXPOSE 8085

ENTRYPOINT ["java", "-jar", "app.jar"]