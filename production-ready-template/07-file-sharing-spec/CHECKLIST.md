# File-Sharing Checklist

| Component | Done? |
|-----------|-------|
| API Gateway: AuthZ, presigned URLs, rate limit | ☐ |
| Metadata API: CRUD, search, versions, shares | ☐ |
| Upload Worker: Chunk assemble, scan, thumbnail, checksum | ☐ |
| Storage Adapter: MinIO + lifecycle + versioning | ☐ |
| Notification Service: Email/push/webhook | ☐ |
| Orchestrator: Saga with compensation | ☐ |

## UI

| Page | Done? |
|------|-------|
| Landing | ☐ |
| Login/Register (OAuth2 + Magic Link) | ☐ |
| Dashboard (virtualized, drag-drop, progress) | ☐ |
| File View (preview, versions, permissions) | ☐ |
| Settings (API keys, webhooks, quota, team) | ☐ |
| Dark Mode (system + manual, no flash) | ☐ |
| WCAG 2.1 AA (axe-core 0 violations) | ☐ |

## Security

| Control | Done? |
|---------|-------|
| OAuth2 + JWT + Magic Link | ☐ |
| Presigned URLs (15-min, single-use) | ☐ |
| Virus Scan (ClamAV, quarantine) | ☐ |
| Encryption (TLS 1.3, SSE-S3) | ☐ |
| Audit Log (every file op) | ☐ |

## Observability

| Metric | Done? |
|--------|-------|
| Upload throughput/latency | ☐ |
| Part upload rate | ☐ |
| Share downloads | ☐ |
| Worker queue depth | ☐ |
| Saga duration | ☐ |

## CI/CD

| Test | Done? |
|------|-------|
| Unit + Contract + Integration ≥ 150 | ☐ |
| Smoke 15 assertions | ☐ |
| Load: 3 scenarios + RESULTS.md | ☐ |
| UI: Playwright + axe-core | ☐ |
| Visual Regression | ☐ |

## Deploy

| Target | Done? |
|--------|-------|
| Fly.io/Render + Custom Domain + TLS | ☐ |
| Status Page | ☐ |
| Live URL in README | ☐ |