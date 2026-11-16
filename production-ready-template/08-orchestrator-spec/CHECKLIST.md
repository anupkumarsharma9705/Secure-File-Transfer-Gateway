# Orchestrator Checklist

| Component | Done? |
|-----------|-------|
| Schema: workflows + workflow_steps | ☐ |
| Engine: register, start, execute, compensate | ☐ |
| Step interface: execute, compensate, retryPolicy | ☐ |
| ProcessUpload saga: 6 steps registered | ☐ |
| Each step: execute + compensate implemented | ☐ |
| Retry policies per step | ☐ |
| Visibility API (list, detail, retry) | ☐ |
| Grafana dashboard | ☐ |
| Metrics: duration, success rate, queue depth | ☐ |

## Testing

| Test | Done? |
|------|-------|
| Unit: step execute/compensate order | ☐ |
| Integration: Testcontainers PG | ☐ |
| Chaos: kill mid-step → compensation | ☐ |
| Load: 1000 concurrent workflows | ☐ |

## Exit Criteria

| Metric | Target |
|--------|--------|
| Throughput | ≥ 500/min |
| Saga p95 | < 30s |
| Compensation success | 100% |
| Visibility API p99 | < 100ms |
| Zero lost workflows | Verified |