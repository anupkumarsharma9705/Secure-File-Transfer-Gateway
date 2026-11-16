# ── Build Stage ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Cache dependencies
COPY pom.xml .
COPY .mvn .mvn
RUN mvn -B -ntp dependency:go-offline -DskipTests

# Build
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# ── Runtime Stage ────────────────────────────────────────────────────────
# Option A: Distroless (smallest, most secure)
FROM gcr.io/distroless/java21-debian12:nonroot

# Option B: Eclipse Temurin JRE (if you need shell/debugging)
# FROM eclipse-temurin:21-jre-alpine
# RUN addgroup -g 1001 -S appgroup && adduser -u 1001 -S appuser -G appgroup
# USER appuser:appgroup

WORKDIR /app

# Copy built artifact
COPY --from=build --chown=65532:65532 /workspace/target/*.jar app.jar

# Runtime config
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -XX:+UseContainerSupport"
ENV SPRING_PROFILES_ACTIVE=prod
ENV SERVER_PORT=8080

# Non-root user (distroless uses 65532:65532)
USER 65532:65532

# Read-only filesystem (tmpfs for /tmp, /var/log)
# Set in docker-compose: tmpfs: [/tmp, /var/log/app]

EXPOSE 8080

# Health check (Spring Boot Actuator)
HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "${JAVA_OPTS}", "-jar", "app.jar"]