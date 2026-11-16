# Operations Guide

## Health Semantics

| Endpoint | Meaning | SLA | Used By |
|----------|---------|-----|---------|
| `GET /health` | Process alive (liveness) | 99.99% | Kubernetes liveness probe |
| `GET /actuator/health/liveness` | Container healthy | 99.99% | Kubernetes liveness probe |
| `GET /actuator/health/readiness` | Ready for traffic (deps OK) | 99.9% | Kubernetes readiness probe, LB |
| `GET /actuator/health` | All components | 99.9% | Manual / debugging |
| `GET /actuator/prometheus` | Metrics exposition | 99.9% | Prometheus scrape |

**Readiness checks:** PostgreSQL connection, Redis ping, downstream backend reachable.

---

## Failure Matrix

| Symptom | Likely Cause | Immediate Action | Investigation |
|---------|--------------|------------------|---------------|
| Readiness 503 | MySQL/Redis down | Check deps, restart pods | `docker compose logs mysql/redis` |
| 401 on all requests | IdP down, JWKS stale | Check IdP status, wait for cache refresh | `curl $OAUTH2_JWK_SET_URI` |
| 429 spike | Legitimate load or attack | Check `gateway_rate_limited_total`, adjust tier | Logs: `attack_type=RATE_LIMITED` |
| 502/503 from backend | Backend down | Check backend logs, restart | `docker compose logs backend` |
| p99 latency > 500ms | GC pressure, DB slow | Check metrics, scale | `gateway_request_duration_seconds`, DB slow query log |
| `gateway_waf_blocked_total` spike | Attack campaign or false positive | Check `attack_type` in audit logs | `SELECT attack_type, count(*) FROM audit_logs WHERE timestamp > now() - interval '1 hour' GROUP BY attack_type` |
| `gateway_idempotency_store_failure_total` > 0 | Redis OOM / network | Check Redis memory, restart | `redis-cli info memory` |
| `gateway_auth_failures_total` > threshold | Credential stuffing / misconfig | Check `reason` label | Logs: `reason=invalid_token|insufficient_scope|tenant_mismatch` |

---

## Rotation Procedures

### IdP Signing Key

1. **Publish new JWKS** *before* IdP signs tokens with it
2. Gateway caches JWKS; unknown `kid` triggers refresh
3. Monitor `gateway.auth.dependency.failure` metric
4. After 2x JWKS TTL (default 24h), old key can be revoked
5. Update `OAUTH2_JWK_SET_URI` if JWKS endpoint changes

### Operator Key (`INTERNAL_OPS_API_KEY`)

```bash
# Generate new key
NEW_KEY=$(openssl rand -hex 32)

# Update in secret manager (Vault/GitHub Environments)
# Example for GitHub:
gh secret set INTERNAL_OPS_API_KEY --body "$NEW_KEY" --repo yourname/repo

# Rolling restart gateway pods (zero-downtime)
kubectl rollout restart deployment/gateway
# Or Fly.io:
fly apps restart gateway --app your-app
```

### Database Password

1. Generate new password: `openssl rand -hex 32`
2. Update MySQL user: `ALTER USER 'appuser'@'%' IDENTIFIED BY 'newpass';`
3. Update secret in Vault/GitHub Environments
4. Rolling restart gateway + backend pods

### TLS Certificates

- Use Let's Encrypt / ACME (automatic on Fly.io/Render)
- For custom certs: update secret, rolling restart
- Monitor expiry: `certbot certificates` or `openssl x509 -in cert.pem -noout -dates`

---

## Compliance Evidence Export

### Summary (JSON)

```bash
FROM=$(date -u -d '24 hours ago' +%Y-%m-%dT%H:%M:%SZ)
TO=$(date -u +%Y-%m-%dT%H:%M:%SZ)
KEY=$(vault kv get -field=ops_key secret/gateway)  # or GitHub Env

curl -fsS "https://api.example.com/compliance/evidence/summary?from=$FROM&to=$TO" \
  -H "X-Internal-Ops-Key: $KEY" | jq .
```

**Response includes:** decision counts, WAF detections (4 types only), rate-limit rejections, idempotency replays, auth failures, distinct tenants/clients, trust transitions, coverage metadata.

### CSV Export

```bash
curl -fsS -D headers.txt \
  "https://api.example.com/compliance/evidence/audit.csv?from=$FROM&to=$TO&limit=10000" \
  -H "X-Internal-Ops-Key: $KEY" \
  -o "audit-evidence-$FROM.csv"
```

**Check truncation:**
```bash
grep -i 'x-evidence-truncated' headers.txt
# true = hit row cap; narrow window and re-export
```

**CSV Safety:** Every field quoted; cells starting with `=`, `+`, `-`, `@` prefixed with `'`.

### Verify Export Was Audited

```sql
SELECT timestamp, ip, client_id, uri, status_code
FROM audit_logs
WHERE attack_type = 'COMPLIANCE_EXPORT'
ORDER BY timestamp DESC LIMIT 5;
```

### Retention & Storage

| Data | Retention | Storage |
|------|-----------|---------|
| Raw audit logs | 7 years | PostgreSQL (partitioned) |
| Exported evidence | 7 years | S3/MinIO (versioned, encrypted) |
| Export metadata | 7 years | PostgreSQL (`audit_logs` with `attack_type=COMPLIANCE_EXPORT`) |

---

## Backup & Restore

| Data | Frequency | Retention | Restore Test |
|------|-----------|-----------|--------------|
| PostgreSQL | Daily + WAL continuous | 7 years | Monthly (staging) |
| Redis | RDB (6h) + AOF (1s) | 7 days | Monthly (data integrity) |
| MinIO | Versioned + replication | 7 years | Quarterly (full restore) |
| Config/Secrets | Git (infra) + Vault | Forever | On every change |

### Restore Procedure (PostgreSQL)

```bash
# 1. Stop gateway/backend
kubectl scale deployment/gateway deployment/backend --replicas=0

# 2. Restore from latest base backup + WAL
pg_basebackup -D /var/lib/postgresql/data -Fp -Xs -P -R -h backup-host

# 3. Start PostgreSQL, verify
systemctl start postgresql
psql -c "SELECT count(*) FROM audit_logs;"

# 4. Restart gateway/backend
kubectl scale deployment/gateway deployment/backend --replicas=3
```

---

## Capacity Planning

| Resource | Current | Warning | Critical | Action |
|----------|---------|---------|----------|--------|
| CPU (gateway) | 45% | 70% | 85% | Scale HPA, optimize queries |
| Memory (gateway) | 60% | 75% | 90% | Increase heap, check leaks |
| Disk (PostgreSQL) | 40% | 75% | 90% | Archive partitions, add storage |
| Redis Memory | 55% | 80% | 95% | Evict LRU, increase memory |
| MinIO Disk | 30% | 70% | 85% | Lifecycle policies, add nodes |
| DB Connections | 60% | 80% | 95% | Increase pool, add read replicas |

---

## Incident Response

### Severity Levels

| SEV | Definition | Response Time | Escalation |
|-----|------------|---------------|------------|
| SEV-1 | Service down, data loss | 15 min | Page on-call + manager |
| SEV-2 | Degraded (latency, errors) | 30 min | Page on-call |
| SEV-3 | Minor (single feature) | 2 hours | Ticket + next business day |
| SEV-4 | Cosmetic / docs | Next sprint | Backlog |

### Runbook Template

```markdown
# Incident: [Title]

## Impact
- Users affected: [count/%]
- Revenue impact: [$]
- SLA breach: [yes/no]

## Timeline
- Detected: [timestamp]
- Mitigated: [timestamp]
- Resolved: [timestamp]

## Root Cause
[5 Whys analysis]

## Action Items
- [ ] Fix: [description] — Owner: [name] — Due: [date]
- [ ] Prevent: [description] — Owner: [name] — Due: [date]
```

---

## Monitoring & Alerting

### Critical Alerts (Page Immediately)

| Alert | Query | Threshold |
|-------|-------|-----------|
| Service Down | `up{job="gateway"} == 0` | 1 min |
| Readiness Failing | `gateway_readiness_status{status="DOWN"} > 0` | 2 min |
| 5xx Spike | `rate(gateway_requests_total{status=~"5.."}[5m]) > 0.05` | 5 min |
| Redis Down | `redis_up == 0` | 1 min |
| PostgreSQL Down | `pg_up == 0` | 1 min |

### Warning Alerts (Ticket + Slack)

| Alert | Query | Threshold |
|-------|-------|-----------|
| High Latency | `histogram_quantile(0.99, gateway_request_duration_seconds_bucket) > 1` | 10 min |
| High Error Rate | `rate(gateway_requests_total{status=~"4.."}[5m]) > 0.1` | 5 min |
| Rate Limit Saturated | `rate(gateway_rate_limited_total[5m]) > 100` | 15 min |
| WAF Spike | `rate(gateway_waf_blocked_total[5m]) > 50` | 10 min |
| Disk Space | `disk_free_percent < 15` | 1 hour |

---

## On-Call Rotation

| Week | Primary | Secondary |
|------|---------|-----------|
| 1 | Alice | Bob |
| 2 | Carol | Dave |
| 3 | Eve | Frank |
| 4 | Grace | Heidi |

**Handoff Checklist (Friday 5pm):**
- [ ] Active incidents documented
- [ ] Upcoming deployments noted
- [ ] Capacity alerts reviewed
- [ ] Secrets rotation status confirmed