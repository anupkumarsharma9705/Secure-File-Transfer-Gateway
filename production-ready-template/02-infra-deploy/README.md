# Infrastructure & Deploy Checklist

**Every service must run via `docker compose up -d --wait` and be healthy in < 120s.**

---

## `docker-compose.yml` — Required Services

| Service | Purpose | Health Check |
|---------|---------|--------------|
| `gateway` / `api` | Entry point | `/actuator/health/readiness` |
| `postgres` / `mysql` | Primary DB | `pg_isready` / `mysqladmin ping` |
| `redis` | Cache, rate limit, sessions | `redis-cli ping` |
| `minio` / `s3` | Object storage | `/minio/health/live` |
| `backend` / `worker` | Business logic | `/actuator/health/readiness` |

**All services:** non-root user, read-only FS, resource limits, restart policy.

---

## `docker-compose.yml` Template

```yaml
version: "3.9"

services:
  gateway:
    build:
      context: ./gateway
      dockerfile: Dockerfile
    image: ${IMAGE_PREFIX}gateway:${TAG:-local}
    user: "1001:1001"
    read_only: true
    tmpfs:
      - /tmp
      - /var/log/app
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}
      SERVER_PORT: 8081
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_DB: ${MYSQL_DB}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD:-}
      BACKEND_URL: http://backend:8080
      INTERNAL_OPS_API_KEY: ${INTERNAL_OPS_API_KEY}
      OAUTH2_ISSUER_URI: ${OAUTH2_ISSUER_URI}
      OAUTH2_JWK_SET_URI: ${OAUTH2_JWK_SET_URI}
      OAUTH2_AUDIENCE: ${OAUTH2_AUDIENCE}
      OAUTH2_REQUIRED_SCOPES: ${OAUTH2_REQUIRED_SCOPES}
    ports:
      - "127.0.0.1:8081:8081"
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8081/actuator/health/liveness"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 45s
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 256M
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
    networks:
      - app-network

  mysql:
    image: mysql:8.0
    user: "999:999"
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
      MYSQL_DATABASE: ${MYSQL_DB}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
    volumes:
      - mysql-data:/var/lib/mysql
    ports:
      - "127.0.0.1:3307:3306"
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-p${MYSQL_ROOT_PASSWORD}"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 30s
    deploy:
      resources:
        limits:
          memory: 512M
    networks:
      - app-network

  redis:
    image: redis:7-alpine
    user: "999:999"
    command: redis-server --requirepass ${REDIS_PASSWORD:-} --maxmemory 256mb --maxmemory-policy allkeys-lru
    volumes:
      - redis-data:/data
    ports:
      - "127.0.0.1:6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD:-}", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5
    deploy:
      resources:
        limits:
          memory: 256M
    networks:
      - app-network

  minio:
    image: minio/minio:latest
    user: "1000:1000"
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    volumes:
      - minio-data:/data
    ports:
      - "127.0.0.1:9000:9000"
      - "127.0.0.1:9001:9001"
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 10s
      retries: 5
      start_period: 30s
    deploy:
      resources:
        limits:
          memory: 512M
    networks:
      - app-network

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    image: ${IMAGE_PREFIX}backend:${TAG:-local}
    user: "1001:1001"
    read_only: true
    tmpfs:
      - /tmp
    environment:
      SERVER_PORT: 8080
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_DB: ${MYSQL_DB}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
    ports:
      - "127.0.0.1:8080:8080"
    depends_on:
      mysql:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-fsS", "http://localhost:8080/actuator/health/liveness"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 30s
    deploy:
      resources:
        limits:
          memory: 256M
    networks:
      - app-network

volumes:
  mysql-data:
  redis-data:
  minio-data:

networks:
  app-network:
    driver: bridge
```

---

## `.env.example` — Every Secret Documented

```bash
# Copy to .env and replace ALL values before deploy

# ── Database ──────────────────────────────────────────────────────────────
MYSQL_ROOT_PASSWORD=replace-me-root
MYSQL_DB=appdb
MYSQL_USER=appuser
MYSQL_PASSWORD=replace-me-app

# ── Redis ─────────────────────────────────────────────────────────────────
REDIS_PASSWORD=replace-me-redis

# ── MinIO ─────────────────────────────────────────────────────────────────
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=replace-me-minio

# ── Gateway ───────────────────────────────────────────────────────────────
SPRING_PROFILES_ACTIVE=prod
INTERNAL_OPS_API_KEY=replace-me-ops-key-32-chars

# OAuth2 (required in prod)
OAUTH2_ISSUER_URI=https://auth.example.com
OAUTH2_JWK_SET_URI=https://auth.example.com/.well-known/jwks.json
OAUTH2_AUDIENCE=my-api
OAUTH2_REQUIRED_SCOPES=api.read,api.write

# ── Image ─────────────────────────────────────────────────────────────────
IMAGE_PREFIX=ghcr.io/your-org/
TAG=latest
```

---

## `Dockerfile` — Multi-Stage, Non-Root, Distroless

```dockerfile
# ── Build Stage ──────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# ── Runtime Stage ────────────────────────────────────────────────────────
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER 65532:65532
EXPOSE 8081
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "${JAVA_OPTS}", "-jar", "app.jar"]
```

---

## Health Endpoints — Required Contract

| Endpoint | Purpose | Must Return |
|----------|---------|-------------|
| `GET /health` | Liveness (k8s) | `200 OK` + `{"status":"UP"}` |
| `GET /actuator/health/liveness` | Container liveness | `200` + components |
| `GET /actuator/health/readiness` | Traffic readiness | `200` iff DB+Redis+Deps UP |
| `GET /actuator/prometheus` | Metrics scrape | Prometheus text format |

**Readiness must check:** DB connection, Redis ping, downstream service reachable.

---

## Deploy Target — Free Tier Checklist

| Platform | Free Tier | Custom Domain | TLS | Notes |
|----------|-----------|---------------|-----|-------|
| Fly.io | 3 shared-cpu VMs, 160GB bandwidth | ✅ | ✅ | Best for containers |
| Render | 1 web service, 90d sleep | ✅ | ✅ | Auto-deploys from GitHub |
| Railway | $5 credit/mo | ✅ | ✅ | Simple, preview envs |
| Netlify/Vercel | Static + Functions | ✅ | ✅ | Frontend only |

**Minimum:** One live URL with HTTPS, custom domain (even `project.yourname.dev`), status page.

---

## Verification Commands

```bash
# Local full stack
docker compose up -d --wait
docker compose ps --format "table {{.Service}}\t{{.Status}}"

# Health checks
curl -fsS http://localhost:8081/health
curl -fsS http://localhost:8081/actuator/health/readiness
curl -fsS http://localhost:8081/actuator/prometheus | head -20

# Resource usage
docker stats --no-stream
```