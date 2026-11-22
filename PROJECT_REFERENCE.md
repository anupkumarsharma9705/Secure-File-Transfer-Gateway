# SFTG — Complete Project Reference for Portfolio Use

**Single source of truth. Every number below is measured from the repository or CI.
If it is not here, do not claim it.**

---

## Project Identity

| Field | Value |
|---|---|
| Name | Secure File Transfer Gateway (SFTG) |
| Repository | https://github.com/anupkumarsharma9705/Secure-File-Transfer-Gateway |
| Stack | Java 21, Spring Boot 3.2, MySQL 8.0, React 19, Vite 8, Docker Compose, GitHub Actions |
| License | Educational / portfolio demonstration |
| Last verified commit | `87a7775` (2026-11-22) |
| Last green CI run | https://github.com/anupkumarsharma9705/Secure-File-Transfer-Gateway/actions/runs/45012286704 |

---

## What the Application Actually Does

### Authentication & Account
- **Register** → email + password → 6-digit OTP sent via Brevo API → verify OTP → account active
- **Login** → email + password → returns HS256 JWT (2-hour expiry) → stored in browser `localStorage`
- **Logout** → current JWT written to `blacklisted_tokens` table (MySQL) → checked on every authenticated request
- **Account lockout** → 5 failed logins in 15 minutes → account locked for 15 minutes (per-account, DB-backed)
- **OTP limits** → 5 attempts per code, 5-minute TTL, fresh code resets attempts (per-code, DB-backed)

### File Operations
- **Upload** → multipart, 100 MB hard cap, UUID-prefixed stored filename, local disk (`FILE_UPLOAD_DIR`), Apache Tika content-based MIME detection, executable-type blocking (`.exe`, `.sh`, `.bat` via MIME), path-traversal protection, 1-hour auto-expiry
- **Download (own)** → authenticated, ownership checked, streams file from disk
- **List** → paginated metadata for current user's files
- **Cleanup** → scheduler every 5 minutes deletes expired files + associated `shared_links` + `download_logs` in FK order

### Sharing
- **Generate link** → authenticated, UUID token, 1-hour expiry, 5-download limit, QR code in UI
- **Public download** → `GET /api/share/download/{token}` — **requires valid JWT** (the recipient must be signed in), validates link active/not-expired/under-limit, increments download counter, writes `DownloadLog` (downloader email + timestamp), returns file
- **Audit** → every share download logged with downloader email and timestamp; login/logout also log IP + User-Agent

### Malware Scanning (Optional)
- **VirusTotal v3** integration, **fail-open by design**
- Flow: SHA-256 hash → lookup existing report → if unknown, upload file (files > 32 MB use one-time upload URL) → poll analysis 2× at 20 s intervals
- **Only `malicious > 0` blocks upload**; `suspicious` is logged but allowed
- No API key configured → scanning skipped entirely, uploads still work
- Network error / quota / timeout → upload proceeds, failure logged

### Rate Limiting
- **Bucket4j** in-memory (`ConcurrentHashMap<String, Bucket>`), per-IP keys
- Limits:
  - Share download: 5 requests / minute
  - Login: 15 requests / 15 minutes
  - Register: 10 requests / 15 minutes
  - OTP verify: 10 requests / 5 minutes
- Headers on every limited response: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, `Retry-After` on 429
- OPTIONS (CORS preflight) never rate-limited

---

## Architecture & Runtime

### Filter Chain (deterministic order)
```
CorrelationIdFilter (servlet-wide, @Order(HIGHEST_PRECEDENCE))
  → RateLimitingFilter (security chain)
  → JwtAuthenticationFilter (security chain)
  → UsernamePasswordAuthenticationFilter
  → ... rest of Spring Security ...
  → Controller
```
- Both custom filters are `@Component` but **disabled as servlet FilterRegistrationBeans** (`setEnabled(false)`) so they run **only once** inside the security chain
- `FilterChainRegistrationTest` asserts: single registration, correct order (rate limit before JWT)

### Containers
- **Backend**: `eclipse-temurin:21-jre-alpine`, non-root `appuser` (UID 1000), port 8000, healthcheck → `/actuator/health/liveness`
- **Frontend**: `nginxinc/nginx-unprivileged:1.27-alpine`, non-root `nginx` (UID 101), port 8080, healthcheck → `wget /`
- **MySQL**: `mysql:8.0`, healthcheck → `mysqladmin ping`
- **Caddy** (optional `tls` profile): `caddy:2.8-alpine`, automatic Let's Encrypt
- **Docker Compose**: `uploads_data` volume for `/app/uploads`, all ports bound to `127.0.0.1` by default

### Frontend Security Headers (nginx)
```
X-Frame-Options: SAMEORIGIN
X-Content-Type-Options: nosniff
Referrer-Policy: strict-origin-when-cross-origin
Permissions-Policy: geolocation=(), microphone=(), camera=()
Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: blob:; connect-src 'self' https:; frame-ancestors 'self'
```
- Same-origin `/api` proxy to backend (avoids CORS in production)

---

## API Surface (12 endpoints)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | /api/auth/register | public | Register + send OTP |
| POST | /api/auth/verify-otp | public | Verify 6-digit OTP |
| POST | /api/auth/login | public | Login → JWT |
| POST | /api/auth/logout | JWT | Blacklist current token |
| GET | /api/account/me | JWT | Profile + full audit log |
| POST | /api/files/upload | JWT | Upload file (multipart) |
| GET | /api/files/download/{filename} | JWT | Download own file |
| GET | /api/files/my-files | JWT | List own files |
| POST | /api/share/generate | JWT | Create share link |
| GET | /api/share/download/{token} | JWT | Download via share link |
| GET | /api/stats/global | JWT | Totals: users, uploads, downloads |
| GET | /api/stats/health | public | Liveness probe |

---

## Test & Quality Evidence (Measured at `87a7775`)

### Backend
```text
Test methods:        127 across 18 test classes
Result:              0 failures, 0 errors, 9 skipped
Skipped:             9 (AuthAndShareIntegrationTest — Testcontainers needs Docker)
Executed locally:    118
Line coverage:       90.1% (684 / 759 lines)
Branch coverage:     76.6% (141 / 184 branches)
Coverage gate:       80% line (enforced in CI via JaCoCo CSV)
Pact contract tests: 5 (AuthContractTest)
Filter-chain tests:  3 (FilterChainRegistrationTest)
Static analysis:     SpotBugs (Max effort, High threshold), PMD (errorprone), Checkstyle (Google style), OWASP Dependency Check (CVSS ≥ 7 fails, skipped without NVD_API_KEY)
Secrets scan:        TruffleHog (non-blocking)
```

### Frontend
```text
JSX files:           15 (8 pages, 5 components, App.jsx, main.jsx)
Unit tests:          0 (documented gap)
Lint:                ESLint (clean)
Production build:    Vite 8 → 613.35 kB JS (190.23 kB gzip) — triggers >500 kB chunk warning
```

### Integration / Smoke
```text
Compose smoke assertions: 12/12 (scripts/smoke-test.sh)
Assertions cover:
  1. Backend health endpoint returns OK
  2. Swagger UI loads
  3. OpenAPI JSON spec available
  4. Frontend serves index.html
  5. Frontend assets load (Vite manifest)
  6. CORS preflight works
  7. Rate-limit headers on auth endpoint
  8. Security headers on frontend (X-Frame-Options)
  9. Security headers on frontend (nosniff)
 10. Database connectivity (actuator readiness implies DB up)
 11. Backend container healthy
 12. Frontend proxies /api to backend
```

### CI Pipeline (7 jobs on every `main` push)
| Job | Command / Purpose |
|---|---|
| Backend Build & Test | `mvn -B clean verify` + SpotBugs + PMD + Checkstyle + OWASP (if key) + TruffleHog |
| Backend Contract Tests | `mvn -B test -Dtest=AuthContractTest` |
| Backend Integration Tests | `mvn -B verify -DskipUnitTests -DskipContractTests` (MySQL service) |
| Frontend Build & Test | `npm ci && npm run lint && npm run build` |
| Docker Build & Scan | Build both images + Trivy SARIF (HIGH/CRITICAL, non-blocking) |
| Compose Smoke Test | `docker compose up --build -d --wait` + 12 assertions |
| Quality Gate | Download JaCoCo CSV → fail if line coverage < 80% |

---

## Benchmark (In-Process Limiter Only)

**Environment:** Windows 11, JDK 21.0.2, i5-12450H (8C/12T), 15.7 GB RAM, Bucket4j 8.15.0  
**Method:** 1M warm-up, 3× 10M measured ops, capacity 1B / 1-day refill (no rejections)

| Scenario | Median ns/op | Median ops/sec | Min–Max ns/op |
|---|---:|---:|---:|
| 1 thread, 1 key | 62.27 | 16,058,525 | 55.01 – 64.19 |
| 4 threads, 4 keys | 54.05 | 18,500,902 | 53.14 – 54.50 |
| 4 threads, 1 key (contended) | 245.34 | 4,075,945 | 239.38 – 251.79 |

**Not measured / not claimed:** End-to-end HTTP p50/p95 latency, baseline-vs-filter delta, production load test.

---

## Database Schema (Core Tables)

```sql
users                   -- id, email, password_hash, enabled, email_verified,
                        -- otp, otp_expiry, otp_attempts, failed_login_attempts,
                        -- account_locked_until, created_at
user_roles              -- user_id, role (ROLE_USER, ROLE_ADMIN)
files                   -- id, stored_filename, original_filename, file_path,
                        -- file_size, mime_type, upload_date, expiry_at, uploaded_by_id
shared_links            -- id, token, expires_at, download_limit, download_count,
                        -- active, created_by, file_id
download_logs           -- id, downloader_email, downloaded_at, shared_link_id
user_activity_logs      -- id, user_email, action, timestamp, metadata, ip_address, user_agent
blacklisted_tokens      -- id, token, expires_at, blacklisted_at, blacklisted_by_email
```

---

## Configuration (Environment Variables)

### Backend (runtime)
| Variable | Required | Default / Notes |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | No | `prod` in compose |
| `DB_URL` | Yes | JDBC MySQL URL |
| `DB_USERNAME` | Yes | |
| `DB_PASSWORD` | Yes | |
| `JWT_SECRET` | Yes | 64-byte base64 |
| `JWT_EXPIRATION_MS` | No | `7200000` (2 hr) |
| `MAIL_HOST` | No | `smtp-relay.brevo.com` |
| `MAIL_PORT` | No | `587` |
| `MAIL_USERNAME` | No | Brevo SMTP user |
| `MAIL_PASSWORD` | No | Brevo SMTP key |
| `BREVO_API_KEY` | Yes | For OTP emails |
| `VIRUSTOTAL_API_KEY` | No | Blank = scanning skipped |
| `CORS_ALLOWED_ORIGIN` | No | Frontend URL |
| `FILE_UPLOAD_DIR` | No | `/app/uploads` (compose volume) |
| `SERVER_PORT` | No | `8000` |

### Frontend (build-time)
| Variable | Required | Default |
|---|---|---|
| `VITE_API_URL` | No | `/api` (same-origin proxy) |

---

## Known Gaps (Honest, Documented, Not Hidden)

| Gap | Status |
|---|---|
| Frontend unit tests | 0 — only ESLint + build + smoke |
| Testcontainers in CI | 9 tests skip (Docker detection issue in GH Actions) |
| HTTP latency benchmark | Not done — only in-process limiter measured |
| Download counter race | Read-modify-write, no lock; concurrent downloads can exceed 5 |
| JWT storage | `localStorage` (XSS risk); no HttpOnly cookie / CSRF protection |
| Rate limiting | In-memory per-instance; resets on restart; bypassable with IP rotation |
| Bucket map | Unbounded — one entry per key, never evicted |
| VirusTotal | Fail-open; unknown/clean ≠ safe; no local AV/sandbox/CDR |
| Trivy / TruffleHog | Non-blocking (`continue-on-error: true`) |
| OWASP Dependency Check | Only runs if `NVD_API_KEY` secret set |
| Historical secrets | Exist in commits `2ea0888` and `658f712` — rotation + history purge needed |
| Encryption at rest | None — files on `uploads_data` volume |
| Multi-instance deploy | Not supported (in-memory limiter, local disk) |

---

## Resume Bullets (Verbatim, Defensible)

### Detailed (3 bullets)
- Built a full-stack secure file-sharing service (Java 21, Spring Boot 3, React 19, MySQL 8, Docker Compose) with 12 REST endpoints: OTP registration, JWT auth with DB-backed token blacklisting, 100 MB uploads with Tika MIME detection, and share links that expire after 1 hour or 5 downloads with per-download audit logs.
- Achieved 90.1% line coverage (684/759) across 127 backend tests in 18 classes, plus 5 Pact consumer contract tests and 12 Docker Compose smoke assertions; CI enforces an 80% JaCoCo gate and runs 7 jobs (tests, contracts, integration, lint/build, Docker+Trivy, smoke, quality gate) on every push.
- Self-audited and fixed a security-filter double-registration bug that made every request consume two rate-limit tokens, cutting the effective login budget from 15 to ~8 per 15 minutes; made filter order deterministic (rate limit → JWT → auth) and added a 3-test regression suite proving single registration and correct order.

### Short (2 bullets)
- Built a Spring Boot 3 + React 19 file-sharing service with JWT auth, MySQL-backed token revocation, optional VirusTotal scanning, and expiring share links; 127 backend tests, 90.1% line coverage, 5 Pact tests, 12 smoke assertions, 7-job CI.
- Found and fixed a double servlet-filter registration that silently halved rate limits; added deterministic filter order and a regression test that fails if the bug returns.

---

## Interview Talking Points (60-Second Demo Script)

1. **Register** → show OTP step → verify
2. **Login** → show JWT in `localStorage`
3. **Upload** 100 MB file → mention cap, Tika, UUID filename
4. **Generate share link** → show 1-hour countdown, 5-download limit
5. **Open link as recipient** → download → show audit log entry
6. **Hammer login endpoint** → show 429 with `X-RateLimit-*` headers
7. **Open `/actuator/health/readiness`** → explain real DB connection check
8. **Show CI page** → 7 green jobs, 90.1% coverage, 5 Pact, 12 smoke

**If asked "what's not done?"** — recite the Known Gaps table above.

---

## Files to Show in Interview

| Purpose | File |
|---|---|
| Security filter fix | `app/app/SFTG-backend/src/main/java/.../SecurityConfig.java` |
| Regression test | `app/app/SFTG-backend/src/test/java/.../FilterChainRegistrationTest.java` |
| Rate limiter | `app/app/SFTG-backend/src/main/java/.../RateLimitingFilter.java` + `RateLimitingService.java` |
| VirusTotal | `app/app/SFTG-backend/src/main/java/.../VirusTotalService.java` |
| Token blacklist | `app/app/SFTG-backend/src/main/java/.../TokenBlacklistService.java` |
| File upload/security | `app/app/SFTG-backend/src/main/java/.../FileService.java` |
| Share logic | `app/app/SFTG-backend/src/main/java/.../FileShareService.java` |
| Health/readiness | `app/app/SFTG-backend/src/main/java/.../DependencyHealthIndicator.java` |
| CI pipeline | `.github/workflows/ci.yml` |
| Smoke test | `scripts/smoke-test.sh` |
| Benchmark | `docs/benchmarks/README.md` + `RateLimiterBench.java` |
| Portfolio evidence | `PORTFOLIO.md` |

---

## Reproduction Commands (Copy-Paste Ready)

```bash
# Backend: tests + coverage
cd app/app/SFTG-backend
./mvnw -B clean verify
# → target/surefire-reports/*.xml (127 tests, 0 failures, 9 skipped)
# → target/site/jacoco/jacoco.csv (90.1% line, 76.6% branch)

# Frontend: lint + build
cd ../SFTG-frontend
npm run lint && npm run build
# → 613.35 kB JS / 190.23 kB gzip, ESLint clean

# Limiter benchmark (from backend dir)
./mvnw -B -q dependency:build-classpath -Dmdep.outputFile=target/bench-cp.txt
# Windows PowerShell:
$cp = "target\classes;" + (Get-Content target\bench-cp.txt -Raw).Trim()
javac -cp $cp -d target\bench ..\..\..\..\docs\benchmarks\RateLimiterBench.java
java -cp "target\bench;$cp" RateLimiterBench
# → prints 3 scenarios with median ns/op

# Full stack smoke (requires Docker)
cd ../../../..
docker compose up --build -d --wait
./scripts/smoke-test.sh
# → 12/12 PASS
```

---

## What You Must NOT Claim

| Claim | Reality |
|---|---|
| "100% malware detection" | False — fail-open, only `malicious>0` blocks |
| "Zero residual risk" | False — documented risk register exists |
| "HTTP p50/p95 measured" | False — only in-process limiter benchmarked |
| "Redis failover tested" | False — no Redis; blacklist is MySQL |
| "Frontend tested" | False — 0 unit tests |
| "All integration tests pass in CI" | False — 9 Testcontainers tests skip |
| "Production load tested" | False — only Compose smoke test |
| "Pen-test certified" | False — no third-party assessment |
| "20 dead components removed" | False — only 2 orphans existed |

---

## Version History (Relevant Commits)

| Commit | Date | Summary |
|---|---|---|
| `87a7775` | 2026-11-22 | Fix double filter registration; add regression test; clean docs; add portfolio pack |
| `3ce09b5` | 2026-11-20 | Previous green baseline (CI run 44786420133) |
| `bc5c4d1` | 2026-11-19 | Same-origin API proxy, TLS profile, 12-assertion smoke test |
| `8f97530` | 2026-11-18 | Unprivileged nginx on 8080 |
| `a7eebaa` | 2026-11-18 | Actuator readiness check in compose wait loop |

---

## Final Checklist Before Interview

- [ ] Repo is public and CI badge shows green
- [ ] You can run `./mvnw -B clean verify` locally and get 127 tests / 90.1%
- [ ] You can run `npm run lint && npm run build` and get clean output
- [ ] You can open `PORTFOLIO.md` and `PROJECT_REFERENCE.md` and explain any line
- [ ] You can name the 7 CI jobs and what each does
- [ ] You can draw the filter chain on a whiteboard
- [ ] You can explain the VirusTotal fail-open policy without hedging
- [ ] You can list the Known Gaps without being prompted
- [ ] You have the green CI run URL bookmarked: https://github.com/anupkumarsharma9705/Secure-File-Transfer-Gateway/actions/runs/45012286704

---

**This document is the contract. If you say something not in here, you are improvising.**