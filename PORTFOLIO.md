# SFTG - Portfolio & Resume Evidence Pack

**Project:** Secure File Transfer Gateway (SFTG)
**Stack:** Java 21, Spring Boot 3, MySQL 8, React 19, Vite, Docker Compose, GitHub Actions
**Repository:** [anupkumarsharma9705/Secure-File-Transfer-Gateway](https://github.com/anupkumarsharma9705/Secure-File-Transfer-Gateway)
**Last verified:** 2026-11-22

> **Rule for this document:** every number is measured or enforced by the repository.
> If I did not measure something, it is listed under [Not measured](#not-measured--do-not-claim)
> instead of being turned into a resume bullet.

---

## 30-second pitch

I built SFTG, a working file-sharing service where users register with an email OTP,
log in with JWT, upload files up to 100 MB, and create share links that expire after
one hour or five downloads. The backend is Spring Boot 3 with MySQL; the frontend is
React 19 behind an nginx same-origin proxy; the whole stack runs in Docker Compose.
I wrote 127 backend tests across 18 classes, reached 90.1% line coverage (684/759),
added 5 Pact contract tests and 12 Compose smoke assertions, and wired a 7-job GitHub
Actions pipeline. While self-auditing, I found that my two security filters were being
registered twice, which silently cut the effective rate-limit budget roughly in half;
I fixed the registration, made the filter order deterministic, and added a regression
test that fails if either problem returns.

---

## What I actually built

| Area | What exists |
|---|---|
| **Authentication** | Register, OTP verification, login, logout, and `GET /api/account/me`. JWT is HS256 with a 2-hour expiry; BCrypt is the default Spring strength (10). Logout writes the token to a MySQL `blacklisted_tokens` table, checked on every authenticated request. |
| **Account protection** | 5 failed logins in 15 minutes locks the account for 15 minutes. OTP is 6 digits, valid for 5 minutes, with 5 attempts per code. Both are backed by database state, not an in-memory map. |
| **File upload** | 100 MB cap, UUID-prefixed stored filename, local disk storage, Apache Tika content-based MIME detection, executable-extension blocking, path-traversal checks, 1-hour expiry, and a cleanup job every 5 minutes. |
| **Sharing** | UUID share token, 1-hour expiry, 5-download limit, public download endpoint, QR code in the UI, and a download audit log with IP address and user agent. |
| **Rate limiting** | Bucket4j per-IP limits: share download 5/min, login 15/15 min, register 10/15 min, OTP verification 10/5 min. Returns `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`, and `Retry-After` on 429. |
| **Malware scanning** | Optional VirusTotal v3 integration. SHA-256 hash lookup first; only unknown files are uploaded (files over 32 MB use a one-time upload URL); 2 polls at 20-second intervals; only `malicious > 0` blocks the upload. Missing key or API failure is fail-open and logged. |
| **Frontend** | React 19 + Vite 8, 15 JSX files (8 pages, 5 components, `App.jsx`, `main.jsx`), protected routes, upload/share/download flows, expiry countdown, and a same-origin `/api` proxy. |
| **Runtime** | Docker Compose with backend, frontend, and MySQL; healthchecks on all three; backend and frontend run as non-root users; nginx sets CSP, `X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, and `Permissions-Policy`; optional Caddy `tls` profile. |
| **CI** | GitHub Actions with backend build/tests, Pact contract tests, a Testcontainers integration job, frontend lint/build, Docker image builds + Trivy SARIF scans, a 12-assertion Compose smoke test, and an 80% line-coverage quality gate. |

The API has **12 REST endpoint mappings** across 5 controllers:

```text
POST /api/auth/register          POST /api/auth/verify-otp
POST /api/auth/login             POST /api/auth/logout
GET  /api/account/me             POST /api/files/upload
GET  /api/files/download/{name}  GET  /api/files/my-files
POST /api/share/generate         GET  /api/share/download/{token}
GET  /api/stats/global           GET  /api/stats/health
```

---

## Evidence table

| Claim | Measured value | Where it comes from |
|---|---:|---|
| Backend test methods | **127** across **18** classes | `target/surefire-reports/*.xml` after `mvn -B clean verify` |
| Test result | **0 failures, 0 errors, 9 skipped** | Same Surefire reports |
| Tests executed locally | **118**; the 9 skips are `AuthAndShareIntegrationTest` (Testcontainers needs Docker) | Same Surefire reports |
| Line coverage | **90.1% (684/759)** | `target/site/jacoco/jacoco.csv` |
| Branch coverage | **76.6% (141/184)** | Same JaCoCo CSV |
| Coverage gate | **80% line**, enforced by CI | `.github/workflows/ci.yml`, `Check Coverage (JaCoCo)` |
| Pact consumer contract tests | **5** | `AuthContractTest`, run in the contract job |
| Filter-chain regression tests | **3** | `FilterChainRegistrationTest` |
| Compose smoke assertions | **12/12** | `scripts/smoke-test.sh`, smoke job |
| CI jobs on a `main` push | **7** | backend, contract, integration, frontend, Docker, smoke, quality gate |
| Green baseline run | [run 44786420133](https://github.com/anupkumarsharma9705/Secure-File-Transfer-Gateway/actions/runs/44786420133), commit `3ce09b5` | All jobs green before the self-audit changes |
| Frontend unit tests | **0** (documented gap) | `package.json` has no test script |
| Frontend production bundle | **613.35 kB** JS, **190.23 kB** gzip | `npm run build`, Vite warning over 500 kB |
| In-process limiter cost | **62.27 ns/op** single-thread; **245.34 ns/op** under 4-thread same-key contention | [Benchmark method and raw output](https://github.com/anupkumarsharma9705/Secure-File-Transfer-Gateway/blob/main/docs/benchmarks/README.md) |

### What the CI actually runs

Every push to `main` runs seven jobs:

1. **Backend Build & Test** - `mvn -B clean verify`, SpotBugs, PMD, Checkstyle, optional OWASP dependency check, TruffleHog.
2. **Backend Contract Tests** - `mvn -B test -Dtest=AuthContractTest`.
3. **Backend Integration Tests** - `mvn -B verify -DskipUnitTests -DskipContractTests` against a MySQL service.
4. **Frontend Build & Test** - `npm ci`, `npm run lint`, `npm run build`.
5. **Docker Build & Scan** - builds both images and uploads Trivy SARIF results.
6. **Compose Smoke Test** - starts the full stack with `docker compose up --build -d --wait` and runs 12 assertions.
7. **Quality Gate** - downloads the JaCoCo report and fails below 80% line coverage.

Honest caveat: the integration job is green, but its 9 Testcontainers tests are
currently **skipped** in GitHub Actions because Testcontainers does not detect the
Docker environment there. I am reporting that as a gap, not claiming those 9 tests
run. The 5 Pact contract tests and the 12 smoke assertions do run.

---

## The bug I am most proud of finding

This is a real self-audit finding, not a hypothetical interview story.

### Symptom

The rate limits felt lower than configured. A 5/minute download limit was not
behaving like 5 requests; a 15/15-minute login limit was not behaving like 15.

### Root cause

Both `JwtAuthenticationFilter` and `RateLimitingFilter` are `@Component` beans.
Spring Boot therefore auto-registered each one as a servlet filter, **and**
`SecurityConfig` also added each one to the Spring Security chain. Every request
ran both filters twice.

The second bug was hiding in plain sight: both filters were added with
`addFilterBefore(..., UsernamePasswordAuthenticationFilter.class)`, which gives
them the same order. Spring Security's own documentation/source says ties are
non-deterministic, so "rate limit before JWT parsing" was not guaranteed.

### Impact

Each request consumed **two** rate-limit tokens instead of one. With a capacity
of 15, the effective login budget became 8 requests per 15 minutes, not 15. With
a capacity of 5, the effective download budget became 3 requests per minute, not
5. The limits were roughly halved, and the exact number depended on the odd/even
capacity.

### Fix

- Added a disabled `FilterRegistrationBean` for each custom filter, so the servlet
  container no longer registers a second copy.
- Registered JWT before `UsernamePasswordAuthenticationFilter`.
- Registered rate limiting before `JwtAuthenticationFilter`, making the order
  deterministic: **rate limit -> JWT parse -> authentication**.
- Added `FilterChainRegistrationTest` with 3 assertions:
  - neither filter is registered directly with the servlet container;
  - each filter appears exactly once in the security chain;
  - the rate limiter's index is lower than the JWT filter's index.
- Ran the targeted test: **3/3 passed**. Then ran the full clean build:
  **127 tests, 0 failures, 90.1% line coverage**.

Files: [`SecurityConfig.java`](https://github.com/anupkumarsharma9705/Secure-File-Transfer-Gateway/blob/main/app/app/SFTG-backend/src/main/java/com/securetransfer/SFTG/security/SecurityConfig.java),
[`FilterChainRegistrationTest.java`](https://github.com/anupkumarsharma9705/Secure-File-Transfer-Gateway/blob/main/app/app/SFTG-backend/src/test/java/com/securetransfer/SFTG/security/FilterChainRegistrationTest.java).

### Why this is a good interview answer

It shows I can read framework behavior, notice a production-impacting bug that
tests did not catch, fix it at the right layer, and leave a regression test that
encodes the invariant. It also shows I do not treat "the tests are green" as proof
that the security configuration is correct.

---

## Persona answers

### 1. Hiring manager - "Impressive, but what did you actually build?"

> I built a complete file-sharing service, not a UI mock. A user can register,
> verify an OTP, log in, upload a file up to 100 MB, list their files, generate a
> share link, and download it from a public link. The link expires after one hour
> or five downloads, and every download is audited. The backend is Spring Boot 3
> with MySQL and 12 REST endpoints; the frontend is React 19 with 8 pages; Docker
> Compose runs the frontend, backend, and database together. I also built the
> quality layer: 127 backend tests, 90.1% line coverage, 5 Pact contract tests, 12
> Compose smoke assertions, and a 7-job CI pipeline. The part I would point to
> first is a self-audit bug: my security filters were registered twice, so every
> request consumed two rate-limit tokens. I fixed it and added a regression test
> that proves the order and single registration.

**What to show in 60 seconds:** the upload -> share -> download flow, the 1-hour
expiry countdown, the 5-download limit, the rate-limit headers on a 429, and the
CI/coverage page. Do not start with the architecture diagram.

### 2. Developer - "Nice craft. Why is there no test, no CI, and 20 dead components?"

The premise is partly outdated and partly fair.

**Fixed / present:**

- **127 backend test methods across 18 classes**, 0 failures, 0 errors.
- **90.1% line coverage (684/759)** and **76.6% branch coverage (141/184)**.
- **5 Pact consumer contract tests** and **12/12 Docker Compose smoke assertions**.
- **7-job GitHub Actions pipeline** on every push to `main`.
- The two genuinely orphaned React components, `FileCard.jsx` and `MetricCard.jsx`,
  were removed. The repo now has **15 JSX files**, all reachable from the app.
- The double-filter bug above was found and fixed, with a 3-test regression suite.

**Still true, and I will say it before you do:**

- **Frontend unit tests: 0.** The frontend is covered only by ESLint, the Vite
  production build, and the Compose smoke test. This is the biggest testing gap.
- **The 9 Testcontainers integration tests skip in CI.** The job is green, but it
  is not exercising that suite yet.
- **The Vite bundle is 613.35 kB (190.23 kB gzip)** and triggers the 500 kB
  chunk-size warning. Route-level code splitting is the next step.
- **Trivy and TruffleHog are non-blocking** (`continue-on-error: true`), so a
  finding does not fail the build. The OWASP dependency check only runs when
  `NVD_API_KEY` is configured.
- The repo had only **two** orphan components, not twenty. I removed those two
  rather than inflating the cleanup.

### 3. Java backend lead - "Show me the filter order, the baseline latency delta, and what happens when Redis dies."

**Filter order (deterministic after the fix):**

```text
CorrelationIdFilter            (servlet-wide, @Order(HIGHEST_PRECEDENCE))
  -> RateLimitingFilter        (security chain, before JWT)
  -> JwtAuthenticationFilter   (security chain, before UsernamePasswordAuthenticationFilter)
  -> UsernamePasswordAuthenticationFilter
  -> ... rest of Spring Security ...
  -> controller
```

`FilterChainRegistrationTest` asserts that each custom filter appears exactly once
and that the rate limiter's index is lower than the JWT filter's index.

**Baseline latency delta:**

I measured the limiter, not the network. The in-process `RateLimitingService`
cost on JDK 21 / Windows 11 / i5-12450H, after 1,000,000 warm-up calls and three
10,000,000-call repetitions:

| Scenario | Median ns/op | Median ops/sec |
|---|---:|---:|
| 1 thread, 1 key | 62.27 | 16,058,525 |
| 4 threads, 4 keys | 54.05 | 18,500,902 |
| 4 threads, 1 shared key | 245.34 | 4,075,945 |

The limiter is tens of nanoseconds per check; the same-key contention case is
about 4x slower. **I did not measure an end-to-end HTTP p50/p95 delta, so I do not
quote one.** A real HTTP benchmark would need a running stack, a baseline build
without the filter, and enough requests to get past the limiter's own 429s. That
is on the roadmap, not in the resume.

**What happens when Redis dies?**

There is no Redis in this project. The JWT blacklist is MySQL-backed:
`TokenBlacklistService.isBlacklisted()` calls the indexed `existsByToken` query on
`blacklisted_tokens`. If MySQL is unavailable:

- An authenticated request throws while checking the blacklist, so the request
  fails closed; it is not silently treated as "not blacklisted."
- `/actuator/health/readiness` reports **DOWN** because
  `DependencyHealthIndicator` opens a real connection and calls `isValid(2)`.
- Public endpoints that skip the JWT filter can still be reached, but anything
  that needs the database will fail.

That is the honest failure mode. If I added Redis later, the same question would
become "what happens when Redis is down?" and the answer would need to be designed
explicitly rather than assumed.

**One more real limitation:** the 5-download counter is a read-modify-write
(`getDownloadCount()`, compare, increment, save) with no row lock or atomic update.
Two concurrent downloads can both read 4 and both proceed, so the cap can be
exceeded under a race. A production fix would use a conditional `UPDATE ... WHERE
download_count < download_limit` or a pessimistic lock.

### 4. AppSec lead - "Zero residual risk? 100% detection? This person hasn't been burned yet."

Correct. I do not claim zero residual risk and I do not claim 100% detection.
Those claims would be false, and an AppSec reviewer would be right to reject them.

**What the scanner actually does:**

- It hashes the file and asks VirusTotal for an existing report first.
- If the hash is unknown, it uploads the file (or uses a one-time upload URL for
  files over 32 MB) and polls the analysis **2 times at 20-second intervals**.
- Only `malicious > 0` blocks the upload. A `suspicious > 0` verdict is logged but
  does **not** block.
- If the API key is blank, scanning is skipped entirely and uploads still work.
- If VirusTotal is down, rate-limited, or returns an error, the upload proceeds
  (fail-open) and the failure is logged.
- An unknown or "clean" verdict is not proof of safety. It means VirusTotal has no
  malicious signal at that moment.

**Residual risk register:**

| Risk | Current behavior | Consequence | Next step |
|---|---|---|---|
| Scanner outage / quota / missing key | Fail-open; upload continues | Malware can pass during an outage or when the key is unset | Queue for async rescan; add a local AV/sandbox or CDR step for high-risk files |
| Unknown file / clean verdict | Allowed | "Not detected" is treated as "safe" | Add policy for unknown verdicts; block or quarantine by risk tier |
| `suspicious > 0` | Logged, not blocked | A suspicious file can be shared | Make the threshold configurable; block above a policy threshold |
| No local AV / sandbox / CDR | None | No defense if VirusTotal misses a sample | Add ClamAV or a sandboxed detonation service where the memory budget allows |
| In-memory rate limiting | `ConcurrentHashMap` of Bucket4j buckets | Per-instance only; resets on restart; bypassable with multiple instances or IP rotation | Move to Redis or a gateway-level limiter for multi-instance deployments |
| Unbounded bucket map | One entry per key, never evicted | Memory growth under many distinct IPs | Add TTL eviction / Caffeine cache |
| JWT in `localStorage` | Axios reads the token from `localStorage` | XSS can exfiltrate the token | Move to `HttpOnly`, `Secure`, `SameSite` cookies with CSRF protection |
| Download counter race | Read-modify-write, no lock | Concurrent downloads can exceed 5 | Atomic conditional update or pessimistic lock |
| Testcontainers skip in CI | 9 tests skipped | Integration path not exercised in CI | Fix Docker detection or run against the CI MySQL service |
| Frontend tests = 0 | ESLint + build + smoke only | UI regressions can ship | Add Vitest + React Testing Library |
| Non-blocking scans | Trivy/TruffleHog `continue-on-error` | Findings do not fail the build | Make high/critical findings blocking |
| Historical secrets | Secrets exist in old commits | Rotation/purge still required | Rotate keys, purge history, enable push protection |
| No encryption at rest | Files live on the `uploads_data` volume | Host/volume access exposes files | Encrypt the volume or encrypt objects before storage |

**What I did do:**

- 100 MB cap, UUID stored filenames, path-traversal checks, executable blocking.
- Tika content-based MIME detection instead of trusting extensions.
- 1-hour file expiry and 5-minute cleanup.
- JWT blacklist in MySQL with an indexed lookup and hourly cleanup.
- Per-IP rate limits with standard headers, plus per-account login lockout and
  per-OTP attempt limits.
- nginx security headers (CSP, frame options, nosniff, referrer policy,
  permissions policy) and non-root containers.
- 12 smoke assertions that include CORS preflight, rate-limit headers, security
  headers, DB readiness, container health, and the same-origin `/api` proxy.

**What I will not say:** "100% detection," "zero residual risk," "military-grade,"
or "fully secure." Those are marketing claims, not engineering claims.

---

## Resume bullets

Use these verbatim; each one is backed by the evidence table above.

### Detailed version (6 bullets)

- Built **SFTG**, a full-stack secure file-sharing service with **Java 21 / Spring
  Boot 3**, **React 19**, **MySQL 8**, and **Docker Compose**: 12 REST endpoints,
  OTP registration, JWT auth, DB-backed token revocation, 100 MB uploads, and
  share links with 1-hour / 5-download limits.
- Wrote **127 backend test methods across 18 classes** (118 executed, 9
  Testcontainers-gated), reaching **90.1% line coverage (684/759)** and **76.6%
  branch coverage**; CI enforces an **80% line-coverage gate** from the JaCoCo report.
- Added **5 Pact consumer contract tests** and **12 Docker Compose smoke
  assertions** covering CORS preflight, rate-limit headers, security headers, DB
  readiness, container health, and the same-origin `/api` proxy.
- Found and fixed a **double servlet-filter registration** that made every request
  consume two rate-limit tokens and cut the effective login budget to 8 attempts
  per 15 minutes; made the filter order deterministic and added a **3-test
  regression suite** proving single registration and rate-before-JWT order.
- Measured the in-process Bucket4j limiter at **62.27 ns/op** single-threaded and
  **245.34 ns/op** under 4-thread same-key contention; explicitly did **not**
  claim an unmeasured HTTP p50/p95 delta.
- Shipped a **7-job GitHub Actions pipeline** (backend tests, Pact contracts,
  integration job, frontend lint/build, Docker + Trivy, Compose smoke, quality
  gate) and documented residual risk instead of claiming zero-risk or 100%
  detection.

### Short version (3 bullets)

- Built a **Java 21 / Spring Boot 3 + React 19** file-sharing service with 12 REST
  endpoints, JWT + MySQL token revocation, OTP registration, 100 MB uploads, and
  expiring share links.
- Achieved **90.1% line coverage (684/759)** across **127 backend tests**, plus 5
  Pact contract tests, 12 Compose smoke assertions, and a 7-job CI pipeline with
  an 80% coverage gate.
- Self-audited and fixed a **security-filter double registration** that halved
  effective rate limits; added a regression test for single registration and
  deterministic rate-before-JWT order.

### LinkedIn / portfolio blurb

> I build backend systems and then try to break my own assumptions. SFTG is a
> Spring Boot + React file-sharing service with JWT auth, MySQL-backed token
> revocation, optional VirusTotal scanning, and expiring share links. It has 127
> backend tests, 90.1% line coverage, Pact contract tests, and a 7-job CI pipeline.
> My favorite part of the project is a self-audit bug: two security filters were
> registered twice, silently halving every rate limit. I fixed it and wrote the
> regression test that proves the order.

---

## 60-second demo script

1. **Register** with an email, show the OTP step, and verify the account.
2. **Log in** and show the JWT-backed session.
3. **Upload** a file; mention the 100 MB cap, Tika MIME detection, and UUID stored
   filename.
4. **Generate a share link**; show the 1-hour countdown and 5-download limit.
5. **Open the public link**, download once, and show the download count / audit log.
6. **Hit a rate-limited endpoint** and show the 429 plus `X-RateLimit-*` headers.
7. **Open `/actuator/health/readiness`** and explain that it checks a real DB
   connection.
8. **Show the CI page**: 7 jobs, 90.1% line coverage, 5 Pact tests, 12/12 smoke
   assertions.

If asked "what is not done?", say: frontend unit tests are 0, the 9 Testcontainers
tests skip in CI, the download counter has a race, and VirusTotal is fail-open.

---

## Not measured / do not claim

These are deliberately excluded from the resume bullets:

- **End-to-end HTTP p50/p95 latency delta.** Only the in-process limiter was
  benchmarked. No baseline-vs-filter HTTP comparison exists.
- **100% malware detection or zero residual risk.** The scanner is a signal, not a
  guarantee, and it fails open.
- **Redis failover.** There is no Redis. The blacklist is MySQL-backed.
- **Frontend unit-test coverage.** There are 0 frontend unit tests.
- **The 9 Testcontainers tests passing in CI.** They currently skip.
- **Production uptime, SLA, or scale.** No production traffic or load test has been
  run. The Compose smoke test proves the stack starts and the main paths respond;
  it is not a load test.
- **Penetration-test or compliance certification.** No third-party pen test, SOC 2,
  or PCI assessment has been performed.
- **"20 dead components."** Only two orphan components existed, and they were
  removed.

---

## Numbers: how to reproduce

### Backend tests and coverage

```powershell
cd app/app/SFTG-backend
.\mvnw.cmd -B clean verify
```

Then read:

- `target/surefire-reports/*.xml` for the 127 test methods, 18 classes, 0 failures,
  9 skips.
- `target/site/jacoco/jacoco.csv` for 684/759 lines and 141/184 branches.

On Linux/macOS, use `./mvnw -B clean verify`.

### Frontend

```powershell
cd app/app/SFTG-frontend
npm run lint
npm run build
```

The build output is 613.35 kB JS / 190.23 kB gzip and prints the >500 kB chunk
warning. There is no `npm test` script, which is the frontend testing gap.

### Rate limiter benchmark

See [`docs/benchmarks/README.md`](docs/benchmarks/README.md) for the environment,
method, raw output, and the exact `javac`/`java` commands. The harness is
[`docs/benchmarks/RateLimiterBench.java`](docs/benchmarks/RateLimiterBench.java).

### CI and smoke test

- Workflow: [`.github/workflows/ci.yml`](.github/workflows/ci.yml)
- Actions: [CI Pipeline](https://github.com/anupkumarsharma9705/Secure-File-Transfer-Gateway/actions/workflows/ci.yml)
- Smoke script: [`scripts/smoke-test.sh`](scripts/smoke-test.sh) (12 assertions)
- Green baseline before the self-audit changes: [run 44786420133](https://github.com/anupkumarsharma9705/Secure-File-Transfer-Gateway/actions/runs/44786420133), commit `3ce09b5`

The self-audit changes were verified locally with a clean `mvn verify` on
2026-11-22: 127 tests, 0 failures, 90.1% line coverage, and the 3/3 filter-chain
regression test. The post-change CI run is linked from the Actions page.

---

## Bottom line

This is a small but real system: it starts, serves traffic, enforces limits, expires
links, runs tests, and has a CI pipeline. Its most valuable engineering artifact is
not the feature list; it is the self-audit story, the regression test, and the
honest residual-risk register.
