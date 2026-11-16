# Exit Criteria Checklist

## Per-Project

| Project | CI Green | Coverage ≥80% | Static Analysis | No CVEs | No Secrets | Container Clean | 5 Docs + ADRs | Smoke | Load Results | Deployed |
|---------|----------|---------------|-----------------|---------|------------|-----------------|---------------|-------|--------------|----------|
| Gateway | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| File-Sharing | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| Third | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |

## Portfolio

| Item | Done? |
|------|-------|
| 3 projects at this level | ☐ |
| 3 distinct shapes | ☐ |
| GitHub Profile README | ☐ |
| 3 Pinned Repos | ☐ |
| 3 Asciinema (60s) | ☐ |
| 20 Apps → 8+ Screens | ☐ |

---

## One-Command Verification

```bash
# Run all checks
./scripts/verify-all.sh
```

Create `scripts/verify-all.sh`:

```bash
#!/bin/bash
set -euo pipefail

echo "=== CI Status ==="
gh run list --limit 1 --json conclusion --jq '.[0].conclusion'

echo "=== Coverage ==="
mvn -B verify > /dev/null 2>&1 && grep -oP 'Total.*?(\d+)%' target/site/jacoco/index.html | head -1

echo "=== Static Analysis ==="
mvn -B verify > /dev/null 2>&1 && echo "PASS" || echo "FAIL"

echo "=== Dependencies ==="
mvn org.owasp:dependency-check-maven:check > /dev/null 2>&1 && echo "PASS" || echo "FAIL"

echo "=== Secrets ==="
trufflehog git file://. --only-verified --no-verification > /dev/null 2>&1 && echo "PASS" || echo "FAIL"

echo "=== Container ==="
trivy image $(docker compose images -q | head -1) --severity HIGH,CRITICAL --exit-code 0 > /dev/null 2>&1 && echo "PASS" || echo "FAIL"

echo "=== Docs ==="
ls README.md ARCHITECTURE.md docs/CONFIGURATION.md docs/OPERATIONS.md docs/ADR/ > /dev/null 2>&1 && echo "PASS" || echo "FAIL"

echo "=== Load Results ==="
ls load-tests/RESULTS.md load-tests/results/*.json > /dev/null 2>&1 && echo "PASS" || echo "FAIL"

echo "=== Live URL ==="
curl -fsS -o /dev/null -w "%{http_code}" https://project.yourname.dev/health
```