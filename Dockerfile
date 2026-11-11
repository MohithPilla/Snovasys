# Multi-stage Docker build for Snovasys Secure Banking System
# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app

# Copy pom.xml and dependency declarations first for layer caching
COPY backend/pom.xml .
RUN mvn dependency:go-offline -B

# Copy backend source code
COPY backend/src ./src

# Build package without running tests during image assembly
RUN mvn clean package -DskipTests

# Stage 2: Minimal Production JRE Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create a non-root system user for security
RUN addgroup -S bankgroup && adduser -S bankuser -G bankgroup
USER bankuser:bankgroup

# Copy compiled JAR artifact from build stage
COPY --from=build /app/target/*.jar app.jar

# Expose default HTTP port
EXPOSE 8080

# Environment variables with production fallbacks
ENV PORT=8080 \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC"

# Run Spring Boot application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --server.port=${PORT}"]
