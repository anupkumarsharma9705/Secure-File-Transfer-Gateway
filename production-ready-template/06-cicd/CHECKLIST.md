# CI/CD Checklist

| Job | Required | Done? |
|-----|----------|-------|
| Build & Unit Test | ✅ | ☐ |
| Contract Tests | ✅ | ☐ |
| Integration Tests (Testcontainers) | ✅ | ☐ |
| Compose Smoke Test (11 assertions) | ✅ | ☐ |
| Load Test (scheduled + on release) | ✅ | ☐ |
| Deploy Preview (PR) | ✅ | ☐ |
| Deploy Production (Tag) | ✅ | ☐ |

## Branch Protection

| Gate | Enforced |
|------|----------|
| All CI jobs pass | ☐ |
| Coverage ≥ 80% | ☐ |
| No HIGH/CRITICAL CVEs | ☐ |
| Secrets scan pass | ☐ |
| PR required (no direct push) | ☐ |
| 1 review required | ☐ |

---

## Verification

```bash
# Local CI simulation
mvn -B clean verify
docker compose up --build -d --wait
./scripts/smoke-test.sh
k6 run load-tests/baseline-test.js
```