# Measurable Exit Criteria — "Done" Definitions

**Every project must hit these numbers before you call it "production-ready."**

---

## Per-Project Targets

### Gateway (Infra Project)

| Metric | Target | Measurement |
|--------|--------|-------------|
| `docker compose up` healthy | < 120s | `time docker compose up -d --wait` |
| Unit + Contract + Integration tests | ≥ 200, 0 failures | `mvn clean verify` |
| Smoke test assertions | 11 | CI log |
| Load: baseline p95 | < 200ms | k6 `RESULTS.md` |
| Load: health p95 | < 50ms | k6 `RESULTS.md` |
| Load: idempotency accuracy | 100% when first succeeds | k6 `RESULTS.md` |
| Error rate under load | 0% 5xx | k6 `RESULTS.md` |
| Deployed URL | Custom domain, HTTPS | `curl -I https://...` |
| Security scan | 0 HIGH/CRITICAL | Trivy/Grype |

### File-Sharing (Product Project)

| Metric | Target |
|--------|--------|
| `docker compose up` healthy | < 120s |
| Unit + Contract + Integration | ≥ 150 tests |
| Smoke assertions | 15 (upload, share, download, 4xx/5xx) |
| Load: upload init p95 | < 100ms @ 100 req/s |
| Load: part upload (direct) p95 | < 50ms @ 500 req/s |
| Load: share download p95 | < 80ms @ 200 req/s |
| Virus scan throughput | ≥ 50 files/min |
| UI accessibility | axe-core 0 violations |
| Visual regression | 0 diffs |
| Deployed URL | Custom domain, HTTPS |

### Third Project (Different Shape)

| Metric | Target |
|--------|--------|
| `docker compose up` healthy | < 120s |
| Unit + Contract + Integration | ≥ 100 tests |
| Smoke assertions | 11 |
| Load: 2 scenarios + RESULTS.md | ✅ |
| Deployed URL | Custom domain, HTTPS |

---

## Cross-Project Standards

| Standard | Target | Verification |
|----------|--------|--------------|
| **CI Green** | All jobs pass on `main` | GitHub Actions badge |
| **Coverage** | ≥ 80% line on business logic | JaCoCo/coverage.xml |
| **Static Analysis** | 0 warnings (error on warn) | SpotBugs/PMD/Checkstyle |
| **Dependencies** | 0 HIGH/CRITICAL CVEs > 7 days | Dependabot + OWASP |
| **Secrets** | 0 in repo, 0 in history | TruffleHog/GitLeaks |
| **Container Scan** | 0 HIGH/CRITICAL | Trivy |
| **Documentation** | 5 core docs + ADRs | File existence |
| **ADRs** | ≥ 3 per project | `docs/ADR/*.md` |
| **Load Results** | `RESULTS.md` + raw JSON | Committed |
| **Live URL** | HTTPS, custom domain | `curl -I` |

---

## Portfolio-Level Targets

| Metric | Target |
|--------|--------|
| **Projects at this level** | 3 |
| **Distinct architectural shapes** | 3 (Gateway, Webapp, CLI/Pipeline/Operator) |
| **GitHub Profile README** | 3 project cards + live URLs |
| **Pinned Repos** | 3 |
| **Asciinema Demos** | 3 (60s each) |
| **Applications → Screens** | 20 apps → 8+ screens |

---

## How to Verify (One Command Each)

```bash
# 1. CI Green
gh run list --limit 1 --json conclusion --jq '.[0].conclusion'  # "success"

# 2. Coverage
mvn -B verify && cat target/site/jacoco/index.html | grep -oP 'Total.*?(\d+)%' | head -1

# 3. Static Analysis
mvn -B verify 2>&1 | grep -c "BUILD SUCCESS"  # 1

# 4. Dependencies
mvn org.owasp:dependency-check-maven:check 2>&1 | grep -c "No vulnerabilities found"

# 5. Secrets
trufflehog git file://. --only-verified --no-verification 2>&1 | wc -l  # 0

# 6. Container
trivy image your-image:tag --severity HIGH,CRITICAL --exit-code 1  # exit 0

# 7. Docs
ls README.md ARCHITECTURE.md docs/CONFIGURATION.md docs/OPERATIONS.md docs/ADR/ | wc -l  # 5

# 8. Load Results
ls load-tests/RESULTS.md load-tests/results/*.json | wc -l  # ≥ 4

# 9. Live URL
curl -fsS -o /dev/null -w "%{http_code}" https://project.yourname.dev/health  # 200
```

---

## Definition of Done Checklist

**Project is "done" only when ALL are ✅:**

- [ ] CI green on `main` (7 jobs)
- [ ] Coverage ≥ 80%
- [ ] Static analysis: 0 warnings
- [ ] No HIGH/CRITICAL CVEs
- [ ] No secrets in repo/history
- [ ] Container scan clean
- [ ] 5 core docs + ≥ 3 ADRs
- [ ] Smoke test: 11+ assertions
- [ ] Load test: RESULTS.md + raw JSON
- [ ] Deployed: custom domain, HTTPS
- [ ] Portfolio: GitHub profile + asciinema