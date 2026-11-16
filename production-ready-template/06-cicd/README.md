# CI/CD Checklist

**GitHub Actions: Build → Test → Contract → Integration → Compose Smoke → Load → Deploy. All gates required.**

---

## Required Workflow: `.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [main, master]
  pull_request:
  schedule:
    - cron: '0 2 * * 0'  # Weekly load test

permissions:
  contents: read
  packages: read
  security-events: write

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

jobs:
  # 1. Build & Unit Test
  build-test:
    name: Build & Unit Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Verify (unit + static analysis)
        run: mvn -B -ntp clean verify -DskipContractTests -DskipIntegrationTests
      - name: Upload JaCoCo
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-reports
          path: '**/target/site/jacoco'
          retention-days: 7

  # 2. Contract Tests
  contract:
    name: Contract Tests
    needs: build-test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Verify contracts
        run: mvn -B -ntp verify -DskipUnitTests -DskipIntegrationTests -Dpact.verify=true

  # 3. Integration Tests (Testcontainers)
  integration:
    name: Integration Tests
    needs: build-test
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        ports: [5432:5432]
        env:
          POSTGRES_PASSWORD: test
          POSTGRES_DB: testdb
        options: --health-cmd="pg_isready" --health-interval=10s --health-timeout=5s --health-retries=5
      redis:
        image: redis:7-alpine
        ports: [6379:6379]
        options: --health-cmd="redis-cli ping" --health-interval=10s --health-timeout=5s --health-retries=5
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - name: Run integration tests
        run: mvn -B -ntp verify -DskipUnitTests -DskipContractTests

  # 4. Compose Smoke Test (Full Stack)
  compose-smoke:
    name: Compose Smoke Test
    needs: [build-test, contract, integration]
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - uses: actions/checkout@v4
      - name: Start stack
        run: |
          cp .env.example .env
          docker compose up --build -d --wait
      - name: Smoke test
        run: |
          set -euo pipefail
          base=http://127.0.0.1:8081
          curl -fsS "$base/health"
          curl -fsS "$base/actuator/health/readiness"
          curl -fsS "$base/api/account/balance?accountId=ACC123" | grep -q '"balance":50000'
          curl -fsS "$base/actuator/prometheus" | grep '^gateway_requests_total' > /dev/null
          pay='{"fromAccountId":"ACC123","toAccountId":"ACC456","amount":500,"currency":"INR","idempotencyKey":"ci-payment-1"}'
          curl -fsS -X POST "$base/api/payments/transfer" -H 'Content-Type: application/json' -H 'Idempotency-Key: ci-payment-1' -d "$pay" | grep -q '"status":"SUCCESS"'
          curl -fsS -D replay.headers -o replay.json -X POST "$base/api/payments/transfer" -H 'Content-Type: application/json' -H 'Idempotency-Key: ci-payment-1' -d "$pay"
          grep -qi '^Idempotency-Replayed: true' replay.headers
          test "$(curl -sS -o /dev/null -w '%{http_code}' "$base/api/account/balance?accountId=ACC123%27%20OR%20%271%27%3D%271")" = 403
          test "$(curl -sS -o /dev/null -w '%{http_code}' -X PROPFIND "$base/api/account/balance?accountId=ACC123")" = 405
          test "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$base/api/payments/transfer" -H 'Content-Type: application/json' -H 'Content-Encoding: gzip' -d 'not-gzip')" = 415
          test "$(curl -sS -o /dev/null -w '%{http_code}' "$base/feedback/summary")" = 401
          curl -fsS "$base/compliance/evidence/summary" -H 'X-Internal-Ops-Key: dev-ops-key-change-me' | grep -q '"attestation"'
          curl -fsS "$base/compliance/evidence/audit.csv?limit=10" -H 'X-Internal-Ops-Key: dev-ops-key-change-me' | grep -q '^id,timestamp,tenant_id'
      - name: Dump logs on failure
        if: failure()
        run: docker compose logs --no-color --tail 200
      - name: Stop stack
        if: always()
        run: docker compose down -v

  # 5. Load Test (Scheduled + On Release)
  load-test:
    name: Load Test
    needs: compose-smoke
    if: github.event_name == 'schedule' || github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: grafana/k6-action@v0.2
        with:
          filename: load-tests/baseline-test.js
          cloud: false
      - name: Upload results
        uses: actions/upload-artifact@v4
        with:
          name: k6-results
          path: load-tests/results/
          retention-days: 30

  # 6. Deploy Preview (PR)
  deploy-preview:
    name: Deploy Preview
    needs: [build-test, contract, integration]
    if: github.event_name == 'pull_request'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Deploy to preview
        run: echo "Deploy to Fly.io/Render preview env"
      - name: Comment PR with URL
        run: echo "Preview: https://pr-${{ github.event.number }}.project.example.com"

  # 7. Deploy Production (Tag)
  deploy-prod:
    name: Deploy Production
    needs: [build-test, contract, integration, compose-smoke]
    if: github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')
    runs-on: ubuntu-latest
    environment: production
    steps:
      - uses: actions/checkout@v4
      - name: Build & Push
        run: |
          docker build -t ghcr.io/${{ github.repository }}:${{ github.ref_name }} .
          docker push ghcr.io/${{ github.repository }}:${{ github.ref_name }}
      - name: Deploy
        run: echo "Deploy to Fly.io/Render production"
```

---

## Required Gates (Branch Protection)

| Gate | Required |
|------|----------|
| Build & Unit Test | ✅ |
| Contract Tests | ✅ |
| Integration Tests | ✅ |
| Compose Smoke Test | ✅ |
| Code Coverage ≥ 80% | ✅ |
| No HIGH/CRITICAL CVEs | ✅ |
| Secrets Scan Pass | ✅ |

---

## Verification

```bash
# Run full CI locally (needs act or GitHub)
# Or just run each stage:
mvn -B clean verify
docker compose up --build -d --wait
./scripts/smoke-test.sh
k6 run load-tests/baseline-test.js
```