# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| `main` (latest) | ✅ |

## Reporting a Vulnerability

Do **not** open a public issue. Email the maintainer with:
- affected endpoint/component
- reproduction steps
- impact assessment

You should receive a response within 72 hours.

## Implemented Controls

| Layer | Control |
|-------|---------|
| Passwords | BCrypt (cost 10), strength policy enforced at registration |
| Sessions | Stateless JWT (HS256, 2 h), DB-backed revocation on logout |
| Login abuse | Per-IP rate limit (Bucket4j) + per-account lockout (5 attempts / 15 min) |
| OTP abuse | 5-attempt budget; code burned on exhaustion; 5-minute TTL |
| File upload | 100 MB cap, path-traversal sanitisation, Tika MIME check, executable block |
| Malware | VirusTotal scan; confirmed malware blocks upload |
| Authorization | Ownership checks on download and share generation |
| Transport | HTTPS at the platform edge; CORS locked to a single origin |
| Headers | X-Frame-Options, X-Content-Type-Options, Referrer-Policy, CSP |
| Audit | Every auth/upload/download/share action logged with IP + User-Agent |
| Dependencies | OWASP Dependency Check (`failBuildOnCVSS=7`) |

## Secret Handling

- No credentials are committed. All secrets are injected by environment.
- `.env` and `application-*.properties` local overrides are git-ignored.
- `.env.example` documents every required variable.
- Rotate any credential that has ever been committed and purge it from history.

## Secure Development Checklist

- [x] Secrets scanning in CI (TruffleHog)
- [x] Dependency CVE gate (OWASP, CVSS ≥ 7 fails build)
- [x] Container image scanning (Trivy, HIGH/CRITICAL fails)
- [x] Input validation on registration
- [x] Centralised exception handling (no stack traces in responses)
- [ ] Refresh-token rotation (planned)
- [ ] File encryption at rest (planned)
