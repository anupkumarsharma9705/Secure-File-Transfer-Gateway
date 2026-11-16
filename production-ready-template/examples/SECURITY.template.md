# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| `main` / `master` | ✅ Security fixes applied |
| Latest tagged release | ✅ Security fixes applied |
| Older snapshots | ❌ Not supported |

## Reporting a Vulnerability

**Use GitHub's Private Vulnerability Reporting:**
1. Go to this repository's **Security** tab
2. Choose **Report a vulnerability**
3. Fill in the form with:
   - Affected commit/branch
   - Deployment mode (`dev` or `prod`)
   - Endpoint and request shape to reproduce
   - Expected vs observed behavior with impact
   - Relevant logs (secrets/PII redacted)
   - Whether exploit requires control of IdP, DB, Redis, backend, or host

**Do NOT:**
- Open a public issue for suspected vulnerabilities
- Include production secrets, access tokens, customer data, or unredacted audit exports

## Response

- No SLA offered (this is not a commercial product)
- Reports reviewed on best-effort basis
- Validated issues fixed before public disclosure
- Please allow reasonable time for fix before publishing details

---

## Scope

### In Scope

- OAuth2/JWT validation, tenant binding, scope enforcement
- Tenant-scoped rate limiting and idempotency
- WAF normalization and request/response bounds
- Operator authentication and compliance evidence endpoints
- Secret handling and container/runtime configuration in this repository

### Out of Scope

- Demo frontend and reference backend (not production services)
- `dev` profile (intentionally disables client auth, trusts `X-Tenant-Id`)
- Vulnerabilities requiring compromised IdP, database, Redis, or host
- Dependency advisories without demonstrated exploit path (still worth reporting, handled as maintenance)

---

## Deployment Expectations

**The gateway is secure only when deployed as documented:**

1. Run with `prod` profile, never `dev`
2. Configure `OAUTH2_ISSUER_URI` or `OAUTH2_JWK_SET_URI`, an audience, and required scopes
3. Terminate TLS in front of the gateway; keep MySQL, Redis, and Actuator on private networks
4. Source secrets from a secret manager (Vault, Doppler, GitHub Environments), not committed or shared `.env`
5. Rotate operator key and IdP signing keys per [docs/OPERATIONS.md](docs/OPERATIONS.md)
6. Keep gateway, MySQL, Redis, and JVM patched

See [docs/CONFIGURATION.md](docs/CONFIGURATION.md) and [docs/OPERATIONS.md](docs/OPERATIONS.md) for full configuration and operating rules.

---

## Security Controls Implemented

| Control | Implementation |
|---------|----------------|
| **Authentication** | OAuth2 Client Credentials + JWT (RS256), local JWKS validation |
| **Authorization** | Tenant binding via `tenant_id` claim, scope enforcement |
| **Rate Limiting** | Redis sorted-set sliding window, per tenant+IP, 4 tiers |
| **WAF** | Regex-based SQLi, XSS, Path Traversal, Command Injection |
| **Idempotency** | Redis `SET NX EX` + Lua CAS, owner-tokened claims |
| **Operator Auth** | SHA-256 + `MessageDigest.isEqual`, lockout after 10 failures |
| **Audit** | Every request → PostgreSQL (async), `client_id` attribution |
| **Headers** | CSP, HSTS, X-Frame-Options, Referrer-Policy, Permissions-Policy |
| **Secrets** | Vault/GitHub Environments; zero in repo; rotation documented |
| **Container** | Non-root, read-only FS, distroless base, resource limits |

---

## Security Testing

| Test | Frequency | Tool |
|------|-----------|------|
| SAST | Every PR | SpotBugs, PMD, Checkstyle, Error Prone |
| Dependency Scan | Every PR + Weekly | OWASP Dependency Check, Dependabot |
| Container Scan | Every Build | Trivy |
| Secret Scan | Every PR + Push | TruffleHog, GitLeaks |
| DAST | Weekly | OWASP ZAP (baseline) |
| Pen Test | Quarterly (planned) | External |

---

## Contact

Security issues: GitHub Private Vulnerability Reporting (Security tab → Report a vulnerability)