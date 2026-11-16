# Testing Checklist

| Layer | Minimum | Done? |
|-------|---------|-------|
| Unit | ≥ 80% line coverage, < 5s/module | ☐ |
| Contract | Pact for every HTTP/gRPC boundary | ☐ |
| Integration | Testcontainers: real DB, Redis, MinIO, Kafka | ☐ |
| E2E Smoke | 11 assertions in CI after `docker compose up` | ☐ |
| Load | k6 scripts + RESULTS.md + raw JSON committed | ☐ |

### Per-Project Test Counts

| Project | Unit | Contract | Integration | Smoke | Load |
|---------|------|----------|-------------|-------|------|
| Gateway | ≥ 100 | ≥ 5 | ≥ 10 | 11 | 3 scenarios |
| File-Sharing | ≥ 80 | ≥ 5 | ≥ 10 | 11 | 3 scenarios |
| Third | ≥ 50 | ≥ 3 | ≥ 5 | 11 | 2 scenarios |

---

## Verification Commands

```bash
# Unit
mvn -B test

# Contract
mvn -B verify -Dpact.verify=true

# Integration
mvn -B verify -DskipUnitTests -DskipContractTests

# Smoke
docker compose up -d --wait
./scripts/smoke-test.sh

# Load
k6 run load-tests/baseline-test.js
```