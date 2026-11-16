# Infrastructure & Deploy Checklist

| Item | Done? |
|------|-------|
| `docker-compose.yml` with all services | ☐ |
| All services: non-root, read-only, resource limits | ☐ |
| Health checks on every service | ☐ |
| `.env.example` documents every secret | ☐ |
| `Dockerfile` multi-stage, distroless/base | ☐ |
| `/health`, `/readiness`, `/prometheus` endpoints | ☐ |
| Readiness checks DB + Redis + deps | ☐ |
| Deployed to free tier (Fly.io/Render/Railway) | ☐ |
| Custom domain + HTTPS working | ☐ |
| Live URL in README | ☐ |
| `docker compose up -d --wait` → healthy < 120s | ☐ |

---

## Verification Commands

```bash
# Full stack
docker compose up -d --wait
docker compose ps --format "table {{.Service}}\t{{.Status}}"

# Health
curl -fsS http://localhost:8081/health
curl -fsS http://localhost:8081/actuator/health/readiness
curl -fsS http://localhost:8081/actuator/prometheus | head -20

# Resources
docker stats --no-stream
```