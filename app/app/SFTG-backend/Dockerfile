# ---- Build stage ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
RUN apk add --no-cache maven bash git
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# ---- Runtime stage (non-root) ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -g 1000 -S appgroup && \
    adduser -u 1000 -S appuser -G appgroup

RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

COPY --from=build --chown=appuser:appgroup /app/target/*.jar app.jar

USER appuser
EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8000/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
