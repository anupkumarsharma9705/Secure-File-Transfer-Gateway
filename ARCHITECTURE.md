# Architecture — SFTG (Secure File Transfer Gateway)

## System Context

SFTG is a full-stack secure file-sharing platform. Users register with email
OTP verification, authenticate with JWTs, upload files that are virus-scanned
and MIME-validated, and share them through expiring, download-limited links.

```
Browser (React 19 + MUI + Vite)
        │  HTTPS / JSON + multipart
        ▼
Spring Boot 3.4 API (Java 21)
        ├── Security filter chain: correlation-id → rate-limit → JWT
        ├── Controllers → Services → JPA repositories
        ├── MySQL 8 (users, files, shared_links, download_logs, audit, blacklist)
        ├── VirusTotal v3 API (SHA-256 lookup, upload only if unknown) — non-blocking scan
        ├── Cloudinary (object storage)
        └── Brevo (transactional OTP email)
```

## Backend Layers

| Layer | Responsibility | Examples |
|-------|----------------|----------|
| Controller | HTTP mapping, validation, response codes | `AuthController`, `FileController` |
| Service | Business rules, orchestration, transactions | `AuthService`, `FileService`, `FileShareService` |
| Repository | Persistence via Spring Data JPA | `UserRepository`, `FileRepository` |
| Security | JWT filter, blacklist, rate limiting, CORS | `JwtAuthenticationFilter`, `RateLimitingFilter` |
| Scheduler | Expiry cleanup, blacklist cleanup, keep-alive | `FileCleanupScheduler` |
| Observability | Correlation IDs, health probes, Prometheus | `CorrelationIdFilter`, `DependencyHealthIndicator` |

## Data Model

```
users ──< user_roles
users ──< files ──< shared_links ──< download_logs
users ──< user_activity_logs (audit, no FK cascade)
blacklisted_tokens (standalone, indexed token, hourly cleanup)
```

Deletion order is explicit in `FileCleanupScheduler`: `download_logs →
shared_links → files` (FK-safe, `@Transactional`).

## Key Flows

### Registration / Login
1. `POST /api/auth/register` → user row (`email_verified=false`) + 6-digit OTP (5 min TTL)
2. `POST /api/auth/verify-otp` → `email_verified=true`
3. `POST /api/auth/login` → BCrypt check → JWT (HS256, 2 h) → audit log
4. `POST /api/auth/logout` → token persisted to `blacklisted_tokens`

### Upload
1. Size check (100 MB) → path-traversal sanitisation
2. Temp file written to upload dir
3. VirusTotal scan — SHA-256 lookup first, upload if unknown (non-blocking on outage, blocks only on confirmed malware)
4. Tika content-based MIME detection → executable types rejected
5. Metadata row saved with 1-hour expiry; audit log written

### Share
1. `POST /api/share/generate` → owner check → UUID token, 1-hour TTL, 5 downloads
2. `GET /api/share/download/{token}` → validate (active/expiry/limit) → stream file → log download

## Observability

| Signal | Endpoint / Mechanism |
|--------|----------------------|
| Liveness | `GET /actuator/health/liveness` |
| Readiness (DB + VirusTotal config) | `GET /actuator/health/readiness` |
| Metrics | `GET /actuator/prometheus` (Micrometer) |
| Correlation | `X-Correlation-ID` header + MDC in every log line |
| Logs | JSON (prod profile), human-readable (dev profile) |

## Decisions

See `docs/ADR/`:
- 001 — DB-backed JWT blacklist
- 002 — Non-blocking virus scanning
- 003 — Tika content-based MIME detection
