# Code Quality Checklist

| Item | Done? |
|------|-------|
| SpotBugs / PMD / Checkstyle / Error Prone in CI | ☐ |
| Build fails on any warning | ☐ |
| Dependency Check (OWASP) in CI | ☐ |
| Zero HIGH/CRITICAL CVEs unfixed > 7 days | ☐ |
| Dependabot configured weekly | ☐ |
| No SNAPSHOT in release builds | ☐ |
| License check (no GPL/AGPL in proprietary) | ☐ |
| Pre-commit: git-secrets / gitleaks | ☐ |
| CI: trufflehog scan on PR | ☐ |
| Container scan: trivy on build | ☐ |
| Structured JSON logs + correlation ID | ☐ |
| Micrometer/Prometheus metrics (low cardinality) | ☐ |
| OpenTelemetry traces | ☐ |

---

## Verification Commands

```bash
# Static analysis
mvn -B verify

# Dependency check
mvn org.owasp:dependency-check-maven:check

# Secrets
trufflehog git file://. --only-verified
gitleaks detect --source .

# Container
trivy image your-image:tag --severity HIGH,CRITICAL
```