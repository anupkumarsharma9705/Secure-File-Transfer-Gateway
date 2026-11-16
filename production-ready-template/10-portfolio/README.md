# Portfolio Presentation — Getting Interview Calls

**Your GitHub profile is your resume. Make it impossible to ignore.**

---

## GitHub Profile README (`README.md` in `yourname/yourname` repo)

```markdown
# Hi, I'm [Name] 👋

**Backend Engineer** • Distributed Systems • Java/Kotlin/Go • AWS/GCP

I build production-grade systems: gateways, file-sharing platforms, data pipelines — with green CI, live URLs, and measured load data.

---

## 🚀 Featured Projects

| Project | Description | Stack | Live | Load Results |
|---------|-------------|-------|------|--------------|
| **[Fintech API Gateway](https://github.com/you/gateway)** | OAuth2, WAF, rate limiting, idempotency, compliance evidence | Java 21, Spring Boot 3, Redis, MySQL | [gateway.you.dev](https://gateway.you.dev) | [RESULTS.md](https://github.com/you/gateway/blob/main/load-tests/RESULTS.md) |
| **[FileShare](https://github.com/you/fileshare)** | Chunked upload, virus scan, thumbnails, shares, saga orchestrator | Java 21, MinIO, Temporal, React | [files.you.dev](https://files.you.dev) | [RESULTS.md](https://github.com/you/fileshare/blob/main/load-tests/RESULTS.md) |
| **[DataPipe](https://github.com/you/datapipe)** | CDC → Kafka → ClickHouse, exactly-once, replay, watermarking | Go, Kafka, ClickHouse, Temporal | [pipe.you.dev](https://pipe.you.dev) | [RESULTS.md](https://github.com/you/datapipe/blob/main/load-tests/RESULTS.md) |

---

## 📊 What My Projects Prove

- **Green CI on every repo** — Build, test, contract, integration, smoke, load
- **Real load data committed** — Not "it works on my machine"
- **Deployed & operable** — Custom domains, HTTPS, health endpoints, metrics
- **Security-first** — OAuth2, WAF, presigned URLs, virus scan, audit logs
- **Observability built-in** — Structured logs, Prometheus, OpenTelemetry, dashboards
- **Documented for operators** — Runbooks, failure matrices, rotation procedures

---

## 🛠 Tech Stack

**Languages:** Java 21, Kotlin, Go, TypeScript, SQL
**Frameworks:** Spring Boot 3, Quarkus, React, Temporal, Kafka
**Infrastructure:** Docker, Kubernetes, Terraform, GitHub Actions, Prometheus, Grafana
**Databases:** PostgreSQL, MySQL, Redis, ClickHouse, MinIO
**Cloud:** AWS (ECS, RDS, S3), GCP (Cloud Run, Cloud SQL), Fly.io

---

## 📫 Contact

- **Email:** you@example.com
- **LinkedIn:** linkedin.com/in/yourname
- **Available for:** Backend/Platform roles, Remote/Hybrid
```

---

## Pinned Repositories (3)

1. **Gateway** — "OAuth2 + WAF + Rate Limit + Idempotency + Compliance Evidence"
2. **FileShare** — "Chunked Upload + Virus Scan + Thumbnails + Saga Orchestrator"
3. **DataPipe** — "CDC → Kafka → ClickHouse, Exactly-Once, Replay, Watermarking"

**Each pinned repo description = one line that says what it is + live URL.**

---

## Asciinema Demos (60s each)

```bash
# Record
asciinema rec gateway-demo.cast -c "docker compose up -d --wait && k6 run load-tests/baseline-test.js"

# Upload
asciinema upload gateway-demo.cast

# Embed in README
[![asciicast](https://asciinema.org/a/XXXXX.svg)](https://asciinema.org/a/XXXXX)
```

**Each demo shows:**
1. `docker compose up -d --wait` (healthy in < 120s)
2. `curl /health` → 200
3. `k6 run load-tests/baseline-test.js` → passes thresholds
4. CI badge green

---

## Resume Lines (Copy-Paste)

> **Designed & deployed 3 production-grade distributed systems** (API Gateway, File-Sharing Platform, Data Pipeline) with **green CI, live URLs, and measured load data**. Each includes OAuth2/OIDC auth, rate limiting, structured observability, and documented failure modes. Deployed on Fly.io/AWS with custom domains, TLS, and status pages.

> **Built a fintech API gateway** handling OAuth2 client-credentials, WAF inspection, tenant-aware rate limiting, atomic idempotency, and PCI-evidence export. **272 tests, 0 failures, 5.5 req/s sustained, p95 11.5ms, 0% errors under load.** Deployed on Fly.io with custom domain.

> **Developed a file-sharing platform** with chunked uploads, ClamAV scanning, thumbnail generation, presigned URLs, and Temporal saga orchestrator. **150+ tests, 15 smoke assertions, 100 req/s upload init, p95 < 100ms.** React + Tailwind UI, WCAG 2.1 AA.

> **Created a data pipeline** (CDC → Kafka → ClickHouse) with exactly-once semantics, watermark tracking, and replay capability. **Go + Temporal, 100+ tests, 10k events/s, exactly-once verified.**

---

## Application Strategy (Quality > Quantity)

| Week | Action |
|------|--------|
| 1 | Polish all 3 repos to exit criteria |
| 2 | GitHub Profile README + 3 pinned + asciinema |
| 3 | Targeted list: 20 companies (fintech, SaaS, health-tech, platform teams) |
| 4 | Apply with **custom cover letter per company** referencing their tech stack |
| 5 | Follow up on LinkedIn to hiring manager / team lead |
| 6 | Interview prep: 4 verbal answers per project (arch, failure modes, scaling, trade-offs) |

**Target:** 8–12 first-round calls / month.

---

## What to Say in "Walk Me Through Your Project"

**Have 4 answers ready per project:**

1. **Architecture** — "Gateway sits in front of backend, terminates TLS, validates JWT, checks rate limit, runs WAF, proxies. Redis for rate limit/idempotency, MySQL for audit."
2. **Hardest Failure Mode** — "IdP outage: gateway caches JWKS, unknown `kid` triggers refresh, returns 503 only for new keys. Verified in CI smoke test."
3. **Scaling Decision** — "Rate limit uses Redis sorted-set sliding window, exact not approximate. Chose accuracy over throughput; 10 req/s per tenant is the contract."
4. **Trade-off** — "Chose custom saga orchestrator over Temporal to avoid infra. Compensation logic is explicit in code, visible in DB. Trade: more code, zero deps."

---

## LinkedIn Headline

> Backend Engineer • Distributed Systems • Java/Go • Production-Grade Systems (Gateway, File-Sharing, Data Pipeline) • Open to Opportunities