# Configuration Reference

## Required (No Defaults — Must Set Before Deploy)

| Variable | Description | Example |
|----------|-------------|---------|
| `MYSQL_ROOT_PASSWORD` | Root database password | `openssl rand -hex 32` |
| `MYSQL_PASSWORD` | Application database password | `openssl rand -hex 32` |
| `INTERNAL_OPS_API_KEY` | 32-char hex for `/compliance`, `/feedback` | `openssl rand -hex 32` |
| `OAUTH2_ISSUER_URI` | Authorization server issuer | `https://auth.example.com` |
| `OAUTH2_JWK_SET_URI` | JWKS endpoint URL | `https://auth.example.com/.well-known/jwks.json` |
| `OAUTH2_AUDIENCE` | Expected JWT `aud` claim | `my-api` |
| `OAUTH2_REQUIRED_SCOPES` | Comma-separated required scopes | `api.read,api.write` |

## Optional (With Defaults)

### Application

| Variable | Default | Description |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | `prod` | `dev` disables auth, trusts `X-Tenant-Id` |
| `SERVER_PORT` | `8080` | HTTP port |
| `SPRING_APPLICATION_NAME` | `api-gateway` | Service name for metrics/logs |

### Database

| Variable | Default | Description |
|----------|---------|-------------|
| `MYSQL_HOST` | `mysql` | Database host |
| `MYSQL_PORT` | `3306` | Database port |
| `MYSQL_DB` | `appdb` | Database name |
| `MYSQL_USER` | `appuser` | Database user |
| `MYSQL_SSL` | `false` | Use SSL for DB connection |
| `MYSQL_ALLOW_PUBLIC_KEY_RETRIEVAL` | `true` | MySQL 8 auth workaround |

### Redis

| Variable | Default | Description |
|----------|---------|-------------|
| `REDIS_HOST` | `redis` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | `` | Empty = no auth |
| `REDIS_SSL` | `false` | Use TLS for Redis |

### Rate Limiting

| Variable | Default | Description |
|----------|---------|-------------|
| `RATE_LIMIT_ENABLED` | `true` | Disable only for isolated tests |
| `RATE_LIMIT_TIER_HIGH` | `50` | Req/s for HIGH trust tier |
| `RATE_LIMIT_TIER_MEDIUM` | `30` | Req/s for MEDIUM trust tier |
| `RATE_LIMIT_TIER_LOW` | `10` | Req/s for LOW trust tier |
| `RATE_LIMIT_TIER_BLACKLISTED` | `0` | Req/s for BLACKLISTED (effectively blocked) |
| `RATE_LIMIT_WINDOW` | `60s` | Sliding window duration |
| `RATE_LIMIT_BLACKLIST_THRESHOLD` | `4` | Blocks in 24h before blacklist |
| `RATE_LIMIT_BLACKLIST_TTL` | `24h` | Blacklist duration |

### Idempotency

| Variable | Default | Description |
|----------|---------|-------------|
| `IDEMPOTENCY_ENABLED` | `true` | Disable only for isolated tests |
| `IDEMPOTENCY_TTL` | `24h` | Replay window |
| `IDEMPOTENCY_REQUIRED_PATHS` | `/api/payments/**` | Ant-style paths requiring idempotency key |
| `IDEMPOTENCY_KEY_HEADER` | `Idempotency-Key` | Header name for idempotency key |

### WAF

| Variable | Default | Description |
|----------|---------|-------------|
| `WAF_ENABLED` | `true` | Disable only for isolated tests |
| `WAF_MAX_BODY_BYTES` | `1048576` | Max request body size (1MB) |
| `WAF_BLOCKED_RESPONSE_CODE` | `403` | HTTP status for blocked requests |

### Trust Tiers

| Variable | Default | Description |
|----------|---------|-------------|
| `TRUST_TIER_HIGH_THRESHOLD` | `80` | Risk score ≥ this = HIGH |
| `TRUST_TIER_MEDIUM_THRESHOLD` | `50` | Risk score ≥ this = MEDIUM |
| `TRUST_TIER_LOW_THRESHOLD` | `20` | Risk score ≥ this = LOW |
| `TRUST_TIER_BLACKLIST_THRESHOLD` | `4` | Blocks in 24h before BLACKLISTED |
| `TRUST_TIER_COOLDOWN` | `24h` | Blacklist TTL |

### Compliance

| Variable | Default | Description |
|----------|---------|-------------|
| `COMPLIANCE_DEFAULT_WINDOW` | `24h` | Default evidence lookback |
| `COMPLIANCE_MAX_WINDOW` | `90d` | Maximum evidence window |
| `COMPLIANCE_DEFAULT_ROWS` | `1000` | Default CSV row limit |
| `COMPLIANCE_MAX_ROWS` | `10000` | Maximum CSV row limit |

### Proxy & CORS

| Variable | Default | Description |
|----------|---------|-------------|
| `TRUST_PROXY` | `false` | Trust `X-Forwarded-For` (only behind trusted LB) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated browser origins |
| `CORS_ALLOWED_METHODS` | `GET,POST,PUT,PATCH,DELETE,OPTIONS` | Allowed HTTP methods |
| `CORS_ALLOWED_HEADERS` | `*` | Allowed request headers |
| `CORS_MAX_AGE` | `3600` | Preflight cache duration (seconds) |

### Backend

| Variable | Default | Description |
|----------|---------|-------------|
| `BACKEND_URL` | `http://backend:8080` | Upstream service URL |
| `BACKEND_TIMEOUT` | `30s` | Upstream request timeout |
| `BACKEND_MAX_RESPONSE_BYTES` | `10485760` | Max upstream response (10MB) |

### Observability

| Variable | Default | Description |
|----------|---------|-------------|
| `METRICS_ENABLED` | `true` | Prometheus metrics |
| `TRACING_ENABLED` | `false` | OpenTelemetry tracing |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `` | OTLP collector endpoint |
| `LOG_LEVEL_ROOT` | `INFO` | Root log level |
| `LOG_LEVEL_GATEWAY` | `DEBUG` | Gateway package log level |

### Security

| Variable | Default | Description |
|----------|---------|-------------|
| `SECURITY_MAX_BODY_BYTES` | `1048576` | Max request body (1MB) |
| `SECURITY_COMPRESSED_BODY_REJECT` | `true` | Reject `Content-Encoding: gzip/deflate` |
| `SECURITY_ALLOWED_METHODS` | `GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS` | HTTP method allowlist |
| `SECURITY_HSTS_MAX_AGE` | `31536000` | HSTS max-age (1 year) |
| `SECURITY_CSP` | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'` | Content Security Policy |

### mTLS (Opt-In)

| Variable | Default | Description |
|----------|---------|-------------|
| `MTLS_ENABLED` | `false` | Enable mutual TLS |
| `SPRING_SSL_BUNDLE_JKS_KEYSTORE` | `` | Path to keystore |
| `SPRING_SSL_BUNDLE_JKS_PASSWORD` | `` | Keystore password |
| `SPRING_SSL_BUNDLE_PEM_CERT` | `` | Path to cert PEM |
| `SPRING_SSL_BUNDLE_PEM_KEY` | `` | Path to key PEM |

---

## Profile Differences

| Setting | `dev` | `prod` |
|---------|-------|--------|
| `SPRING_PROFILES_ACTIVE` | `dev` | `prod` |
| OAuth2 Auth | **Disabled** (trusts `X-Tenant-Id`) | **Required** (fails if not configured) |
| WAF | Enabled | Enabled |
| Rate Limit | Enabled | Enabled |
| Idempotency | Enabled | Enabled |
| Actuator | All endpoints exposed | `/health`, `/readiness`, `/prometheus` only |
| Swagger UI | Enabled | Disabled |
| Log Level | `DEBUG` | `INFO` |
| HSTS | Disabled | Enabled (1 year) |

---

## Validation Rules

| Variable | Rule |
|----------|------|
| `MYSQL_ROOT_PASSWORD` | ≥ 32 chars, not default |
| `MYSQL_PASSWORD` | ≥ 32 chars, not default |
| `INTERNAL_OPS_API_KEY` | Exactly 64 hex chars (32 bytes) |
| `OAUTH2_ISSUER_URI` | Valid HTTPS URL |
| `OAUTH2_JWK_SET_URI` | Valid HTTPS URL |
| `OAUTH2_AUDIENCE` | Non-empty, matches IdP config |
| `OAUTH2_REQUIRED_SCOPES` | Non-empty, comma-separated |
| `INTERNAL_OPS_API_KEY` | 64 hex chars |
| `REDIS_PASSWORD` | If set, ≥ 16 chars |
| `RATE_LIMIT_TIER_*` | Positive integers, HIGH > MEDIUM > LOW > BLACKLISTED |
| `COMPLIANCE_MAX_WINDOW` | ≥ `COMPLIANCE_DEFAULT_WINDOW` |
| `COMPLIANCE_MAX_ROWS` | ≥ `COMPLIANCE_DEFAULT_ROWS` |

---

## Example `.env` (Production)

```bash
# ── Database ──────────────────────────────────────────────────────────────
MYSQL_ROOT_PASSWORD=a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6
MYSQL_DB=appdb
MYSQL_USER=appuser
MYSQL_PASSWORD=q7w8e9r0t1y2u3i4o5p6a7s8d9f0g1h2

# ── Redis ─────────────────────────────────────────────────────────────────
REDIS_PASSWORD=z3x4c5v6b7n8m9q0w1e2r3t4y5u6i7o8

# ── Gateway ───────────────────────────────────────────────────────────────
SPRING_PROFILES_ACTIVE=prod
INTERNAL_OPS_API_KEY=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef

# OAuth2 (Required in prod)
OAUTH2_ISSUER_URI=https://auth.mycompany.com
OAUTH2_JWK_SET_URI=https://auth.mycompany.com/.well-known/jwks.json
OAUTH2_AUDIENCE=my-api-gateway
OAUTH2_REQUIRED_SCOPES=gateway.read,gateway.write

# ── Rate Limiting ─────────────────────────────────────────────────────────
RATE_LIMIT_TIER_HIGH=100
RATE_LIMIT_TIER_MEDIUM=50
RATE_LIMIT_TIER_LOW=20
RATE_LIMIT_TIER_BLACKLISTED=0

# ── Observability ─────────────────────────────────────────────────────────
LOG_LEVEL_ROOT=INFO
LOG_LEVEL_GATEWAY=INFO
TRACING_ENABLED=true
OTEL_EXPORTER_OTLP_ENDPOINT=https://otel.mycompany.com:4318

# ── Security ──────────────────────────────────────────────────────────────
TRUST_PROXY=true
CORS_ALLOWED_ORIGINS=https://app.mycompany.com,https://admin.mycompany.com
SECURITY_HSTS_MAX_AGE=31536000
```