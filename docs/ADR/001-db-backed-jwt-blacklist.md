# ADR 001: Database-Backed JWT Token Blacklist

**Date:** 2025-01-15
**Status:** Accepted
**Deciders:** Anup Kumar Sharma

## Context

JWT tokens are stateless by design — once issued, they cannot be revoked by the server without additional infrastructure. Common approaches:

1. **In-memory blacklist** (HashMap/ConcurrentHashMap) — Simple but lost on server restart
2. **Redis blacklist** — Fast, survives restarts, but adds infrastructure dependency
3. **Database blacklist** — Persistent, no extra infrastructure, but slower lookups
4. **Short expiry + refresh tokens** — Reduces window but doesn't solve immediate revocation

### Problem with In-Memory Approach

```
In-memory approach problem:
  Server restarts → blacklisted tokens become valid again
  Attacker with stolen token just waits for deployment
  Horizontal scaling → each instance has different blacklist
```

## Decision

**Use MySQL database table `blacklisted_tokens` with indexed `token` column for JWT blacklisting.**

### Implementation Details

- **Table**: `blacklisted_tokens` with columns: `id`, `token` (VARCHAR, indexed), `blacklisted_at`, `expires_at`
- **On logout**: Insert token into blacklist with expiry = token's original expiry
- **On each request**: `SELECT EXISTS(SELECT 1 FROM blacklisted_tokens WHERE token = ? AND expires_at > NOW())`
- **Cleanup**: Hourly `@Scheduled` job deletes expired entries (`DELETE WHERE expires_at < NOW()`)
- **Index**: `CREATE INDEX idx_blacklisted_token ON blacklisted_tokens(token)` for O(log n) lookup

### Code References

- `TokenBlacklistService.java` — blacklist/check/cleanup logic
- `JwtAuthenticationFilter.java` — checks blacklist on every request (line 74-77)
- `FileCleanupScheduler.java` — pattern for scheduled cleanup (lines 27-29)

## Consequences

### Positive
- ✅ Tokens remain blacklisted across server restarts
- ✅ Works with horizontal scaling (shared DB)
- ✅ No additional infrastructure (Redis) required
- ✅ Automatic cleanup prevents unbounded table growth
- ✅ Index provides fast lookups (~1-2ms)

### Negative
- ⚠️ Additional DB round-trip per request (mitigated by index)
- ⚠️ Slightly higher latency than in-memory/Redis
- ⚠️ DB becomes single point of failure for auth (already true for user data)

### Neutral
- 📝 Could migrate to Redis later if latency becomes bottleneck
- 📝 Table size bounded by active token count × 2hr expiry

## Alternatives Considered

| Alternative | Rejected Because |
|-------------|------------------|
| In-memory map | Security hole: restart = token resurrection |
| Redis | Extra infrastructure, overkill for current scale |
| Short JWT (15min) + Refresh | Doesn't solve immediate logout revocation; adds complexity |
| Token versioning in user table | Schema change per user; still needs DB check |

## Related ADRs

- ADR 002: Non-Blocking Virus Scanning (similar pattern: graceful degradation)
- ADR 004: Cascade Delete Order (explicit transactional cleanup)