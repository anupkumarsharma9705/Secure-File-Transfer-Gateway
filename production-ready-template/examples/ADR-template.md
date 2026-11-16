# ADR Template

Copy to `docs/ADR/NNNN-short-title.md` (NNNN = 4-digit sequence number).

---

# ADR NNNN: [Title]

## Status

[Proposed | Accepted | Superseded | Deprecated]

## Context

[Describe the problem, constraints, and forces at play. What decision needs to be made? What are the requirements?]

## Decision

[State the decision clearly and concisely. What did you choose?]

## Consequences

### Positive

- [Benefit 1]
- [Benefit 2]
- [Benefit 3]

### Negative

- [Drawback 1]
- [Drawback 2]
- [Drawback 3]

### Risks

- [Risk 1]: [Mitigation]
- [Risk 2]: [Mitigation]

## Alternatives Considered

| Alternative | Pros | Cons | Why Not Chosen |
|-------------|------|------|----------------|
| [Alt 1] | [Pro] | [Con] | [Reason] |
| [Alt 2] | [Pro] | [Con] | [Reason] |
| [Alt 3] | [Pro] | [Con] | [Reason] |

## Implementation Notes

- [Key implementation detail 1]
- [Key implementation detail 2]
- [Link to code: `src/main/java/.../ClassName.java`]

## Related ADRs

- [ADR NNNN](NNNN-related-title.md)
- [ADR NNNN](NNNN-another-title.md)

## References

- [External link 1](URL)
- [External link 2](URL)

---

## Example: ADR 0001

# ADR 0001: Redis Sorted-Set for Exact Sliding-Window Rate Limiting

## Status
Accepted

## Context
Need distributed, per-tenant+IP rate limiting with exact sliding-window semantics. Approximate algorithms (token bucket, fixed window) allow bursts at window boundaries, which violates our per-tenant SLA.

## Decision
Use Redis sorted sets (`ZSET`) with timestamps as scores. Window = `now() - window_duration`. Atomic Lua script removes expired entries, counts remaining, and conditionally adds new entry.

## Consequences

### Positive
- Exact sliding window (no boundary bursts)
- Sub-millisecond latency
- Horizontal scaling via Redis Cluster
- Atomic check-and-increment

### Negative
- Additional infrastructure (Redis)
- Memory grows with request rate × window
- Lua script debugging complexity

### Risks
- Redis OOM under sustained high load: mitigated by `maxmemory-policy allkeys-lru` + monitoring
- Clock skew: mitigated by using server-side `TIME` in Lua

## Alternatives Considered

| Alternative | Pros | Cons | Why Not |
|-------------|------|------|---------|
| Token Bucket (Redis) | Simple, low memory | Allows bursts at refill | Violates SLA |
| Fixed Window (Redis INCR) | Simplest, minimal memory | 2x burst at boundaries | Unacceptable |
| In-Memory (Caffeine) | Zero deps, fastest | Not distributed | Multi-instance fails |
| Consul Rate Limit | Built-in | Extra dep, higher latency | Overkill |

## Implementation Notes

- Lua script: `src/main/resources/lua/rate-limit.lua`
- Configuration: `RATE_LIMIT_TIER_*`, `RATE_LIMIT_WINDOW`
- Metrics: `gateway_rate_limited_total`, `gateway_rate_limit_window_seconds`

## Related ADRs

- [ADR 0002](0002-lua-idempotency.md) — Lua CAS for idempotency
- [ADR 0005](0005-oauth2-local-validation.md) — Local JWT validation

## References

- [Redis Rate Limiting Patterns](https://redis.io/docs/manual/patterns/rate-limiter/)
- [Sliding Window Log Algorithm](https://en.wikipedia.org/wiki/Rate_limiting#Sliding_window_log)