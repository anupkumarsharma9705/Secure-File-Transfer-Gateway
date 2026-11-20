# Operations Runbook — SFTG

## Service Topology

| Service | Port | Health probe |
|---------|------|--------------|
| caddy (`tls` profile) | 80 / 443 | `GET /` (TLS) |
| frontend (nginx, unprivileged UID 101) | host 80 → container 8080 | `GET /` |
| backend (Spring Boot) | 8000 (localhost only) | `GET /actuator/health/liveness` |
| mysql | 3306 (localhost only) | `mysqladmin ping` |

Virus scanning is off-box (VirusTotal API), so there is no local scanner
container to probe. Readiness reports `virustotal: configured` or
`virustotal: not configured (scans skipped)`.

## Start / Stop

```bash
docker compose up -d --wait          # start, wait for healthchecks
docker compose ps                    # status
docker compose logs -f backend       # tail logs
docker compose down                  # stop (volumes preserved)
docker compose down -v               # stop + delete data (destructive)
```

For the public HTTPS stack (Caddy on 80/443):

```bash
docker compose --profile tls up -d --build --wait
```

## Health Verification

```bash
curl -fsS http://localhost:8000/actuator/health/liveness
curl -fsS http://localhost:8000/actuator/health/readiness
curl -fsS http://localhost:8000/actuator/prometheus | head
./scripts/smoke-test.sh              # 12 assertions
```

## Failure Matrix

| Failure | Symptom | Detection | Response | Recovery |
|---------|---------|-----------|----------|----------|
| MySQL down | API 500s, readiness DOWN | readiness probe | Isolate DB, fail fast | Restart mysql; verify volume |
| VirusTotal down/outage | Uploads continue with a warning | `virustotal` log errors | Accept uploads (ADR-002), alert | Wait for recovery; review uploads since outage |
| VirusTotal rate limit | Scan skipped, warning logged | metrics/log error rate | Continue (non-blocking) | Wait for quota window; lower `VIRUSTOTAL_UPLOAD_UNKNOWN` |
| VirusTotal key missing | `virustotal: not configured` in readiness | readiness detail | Accept uploads; set `VIRUSTOTAL_API_KEY` | Restart backend with the key set |
| Brevo down | Registration blocked (OTP not sent) | 5xx on register, email errors | Surface retry message | Retry; fall back to SMTP relay |
| Cloudinary down | Upload fails after scan | 5xx on upload | Return 503 with retry hint | Restore credentials/service |
| JWT secret rotated | All sessions invalid | mass 401s | Expected | Users log in again |
| Disk full (uploads) | Upload 500, cleanup errors | disk metrics | Free space; cleanup scheduler drains expired files | Scale volume |
| Backend cold start (Render) | 60–90 s first request | synthetic check | Frontend retries (3×, 5 s) | Keep-alive ping (`APP_BASE_URL`) |
| Deployment bad | Health checks fail | CI smoke + canary | Roll back previous image tag | Redeploy last green tag |

## Scheduled Jobs

| Job | Cadence | Purpose |
|-----|---------|---------|
| `FileCleanupScheduler.deleteExpiredFiles` | 5 min | FK-safe removal of expired files |
| `TokenBlacklistService.cleanupExpiredTokens` | 1 h | Prune expired blacklist rows |

## Incident Checklist

1. Confirm blast radius via `/actuator/health` and the failure matrix.
2. Capture correlation IDs from logs (`X-Correlation-ID` is returned to clients).
3. Mitigate (restart dependency / roll back deploy) before root-causing.
4. Write a short post-incident note in the PR/issue.
