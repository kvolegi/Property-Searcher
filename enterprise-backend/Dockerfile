# Stage 1: Build & Package
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace
RUN apk add --no-cache maven

COPY pom.xml .
RUN mvn dependency:go-offline -B || true

COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Hardened Production Runtime
FROM eclipse-temurin:21-jre-alpine
LABEL maintainer="kvolegi <milenapetrushenko0@gmail.com>"
LABEL description="Property-Searcher Enterprise Backend with Spring Boot 3"

# Create unprivileged system group and user
RUN addgroup -S spring && adduser -S spring -G spring

WORKDIR /app

# Copy artifact from builder stage with proper ownership
COPY --from=builder --chown=spring:spring /workspace/target/*.jar app.jar

# Run as non-root user
USER spring

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -q -O - http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
