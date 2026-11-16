# Testing Checklist

**Test Pyramid: Unit → Contract → Integration → E2E Smoke → Load. All green in CI.**

---

## 1. Unit Tests (Fast, Isolated, Deterministic)

| Requirement | Standard |
|-------------|----------|
| Coverage | ≥ 80% line on business logic |
| Framework | JUnit 5 + Mockito / JUnit Jupiter |
| Naming | `*Test.java` in `src/test` |
| Speed | < 5s total per module |
| No external deps | No DB, Redis, HTTP, FS |

**Patterns:**
- Test behavior, not implementation
- One assertion per test (or grouped logically)
- `given-when-then` or `arrange-act-assert`
- Parameterized tests for edge cases

---

## 2. Contract Tests (Consumer-Driven)

| Tool | Use Case |
|------|----------|
| Pact (JVM/JS/Go) | HTTP/gRPC consumer-provider contracts |
| Spring Cloud Contract | Spring-only, stub runner |

**Flow:**
1. Consumer writes test → generates pact file
2. Pact published to broker (or Git)
3. Provider verifies against pact in CI
4. `can-i-deploy` check before release

---

## 3. Integration Tests (Testcontainers)

| Dependency | Testcontainer Module |
|------------|---------------------|
| PostgreSQL | `postgres:16` |
| MySQL | `mysql:8.0` |
| Redis | `redis:7-alpine` |
| MinIO | `minio/minio:latest` |
| Kafka | `confluentinc/cp-kafka:7.5` |
| LocalStack | AWS services (S3, SQS, DynamoDB) |

**Rules:**
- `@Testcontainers` + `@Container` per class
- Reuse containers across tests (`withReuse(true)`)
- Clean DB per test (`@Sql` or `TRUNCATE`)
- Run in CI (GitHub Actions has Docker)

---

## 4. E2E Smoke Tests (GitHub Actions)

**Run after `docker compose up` in CI:**

| # | Check | Command |
|---|-------|---------|
| 1 | Health | `curl -fsS /health` |
| 2 | Readiness | `curl -fsS /actuator/health/readiness` |
| 3 | Happy path | `curl -fsS /api/...` → 200 + expected body |
| 4 | Auth required | `curl -fsS /protected` → 401 |
| 5 | Invalid input | `curl -fsS /api?bad=1` → 400 |
| 6 | Forbidden | `curl -fsS /admin` → 403 |
| 7 | Not found | `curl -fsS /missing` → 404 |
| 8 | Rate limit | 11 rapid requests → 429 |
| 8 | Gzip body | `Content-Encoding: gzip` → 415 |
| 10 | Disallowed method | `PROPFIND` → 405 |
| 11 | Idempotency | Same key twice → 200 + `Replayed` header |

**All must pass.** No "flaky" exemptions.

---

## 5. Load Tests (k6)

| Scenario | Script | Thresholds |
|----------|--------|------------|
| Baseline | `baseline-test.js` | p95 < 200ms, error rate < 1% |
| Health | `health-latency-test.js` | p95 < 50ms |
| Idempotency | `idempotency-test.js` | 100% accuracy when first succeeds |
| Stress | `stress-test.js` | Sustained 2x expected load 5min |
| Soak | `soak-test.js` | 1hr at 80% capacity, no leaks |

**Output:** `load-tests/RESULTS.md` + raw JSON in `load-tests/results/`

---

## CI Test Stages

```yaml
jobs:
  unit:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
      - run: mvn -B test  # unit only

  contract:
    needs: unit
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: mvn -B verify -DskipUnitTests -Dpact.verify=true

  integration:
    needs: unit
    runs-on: ubuntu-latest
    services:
      postgres: { image: postgres:16, ports: [5432] }
      redis: { image: redis:7, ports: [6379] }
    steps:
      - uses: actions/checkout@v4
      - run: mvn -B verify -DskipUnitTests -DskipContractTests

  compose-smoke:
    needs: [unit, contract, integration]
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - run: docker compose up --build -d --wait
      - run: ./scripts/smoke-test.sh  # 11 assertions

  load-test:
    needs: compose-smoke
    if: github.event_name == 'schedule' || github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: grafana/k6-action@v0.2
        with:
          filename: load-tests/baseline-test.js
          cloud: false
```

---

## Verification Commands

```bash
# Unit only
mvn -B test

# Contract
mvn -B verify -Dpact.verify=true

# Integration (needs Docker)
mvn -B verify -DskipUnitTests -DskipContractTests

# Smoke (local)
docker compose up -d --wait
./scripts/smoke-test.sh

# Load
k6 run load-tests/baseline-test.js
```