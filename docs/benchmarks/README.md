# Rate limiter benchmark

This benchmark measures the in-process cost of `RateLimitingService.tryConsume`
(Bucket4j 8.15.0). It is deliberately **not** an HTTP latency benchmark: there
are no sockets, no Spring context, and no database calls. It answers "how much
CPU does the limiter itself add per checked request?", not "what is the p50/p95
of an API call?"

## Environment

| Item | Value |
|---|---|
| Date | 2026-11-22 |
| OS | Windows 11 Home, 10.0.26300, 64-bit |
| CPU | 12th Gen Intel Core i5-12450H (8 cores / 12 logical processors) |
| RAM | 15.7 GB |
| JDK | Eclipse Temurin 21.0.2+13-LTS |
| Bucket4j | 8.15.0 |
| Harness | [`RateLimiterBench.java`](RateLimiterBench.java) |

## Method

- 1,000,000 warm-up calls per scenario.
- 3 measured repetitions of 10,000,000 calls each.
- Median of the 3 repetitions is reported; min/max are included.
- Capacity is 1,000,000,000 with a one-day refill, so no call is rejected and
  the measurement is pure consume cost.
- Scenarios:
  - 1 thread, 1 key (the common single-user path).
  - 4 threads, 4 distinct keys (parallel users, no shared bucket).
  - 4 threads, 1 shared key (worst-case contention on one bucket).
- For the multi-thread scenarios, `ns/op` is aggregate throughput
  (total elapsed time divided by all consumed operations), not per-thread
  latency.

## Results

| Scenario | Median ns/op | Median ops/sec | Min–max ns/op |
|---|---:|---:|---:|
| 1 thread, 1 key | 62.27 | 16,058,525 | 55.01–64.19 |
| 4 threads, 4 keys | 54.05 | 18,500,902 | 53.14–54.50 |
| 4 threads, 1 key | 245.34 | 4,075,945 | 239.38–251.79 |

Raw repetition output:

```text
scenario=1-thread-1-key
  rep=1 total_ms=641.9 ns_per_op=64.19 ops_per_sec=15578656
  rep=2 total_ms=622.7 ns_per_op=62.27 ops_per_sec=16058525
  rep=3 total_ms=550.1 ns_per_op=55.01 ops_per_sec=18178219
  median_ns_per_op=62.27 min=55.01 max=64.19 median_ops_per_sec=16058525

scenario=4-threads-4-keys
  rep=1 total_ms=531.4 ns_per_op=53.14 ops_per_sec=18819533
  rep=2 total_ms=540.5 ns_per_op=54.05 ops_per_sec=18500902
  rep=3 total_ms=545.0 ns_per_op=54.50 ops_per_sec=18349008
  median_ns_per_op=54.05 min=53.14 max=54.50 median_ops_per_sec=18500902

scenario=4-threads-1-key
  rep=1 total_ms=2517.9 ns_per_op=251.79 ops_per_sec=3971569
  rep=2 total_ms=2453.4 ns_per_op=245.34 ops_per_sec=4075945
  rep=3 total_ms=2393.8 ns_per_op=239.38 ops_per_sec=4177535
  median_ns_per_op=245.34 min=239.38 max=251.79 median_ops_per_sec=4075945
```

## How to reproduce

From `app/app/SFTG-backend`:

```bash
# 1. Compile the backend and write its dependency classpath to a file.
./mvnw -B clean verify
./mvnw -B -q dependency:build-classpath -Dmdep.outputFile=target/bench-cp.txt

# 2. Compile and run the harness (Linux/macOS classpath separator is ':').
javac -cp "target/classes:$(cat target/bench-cp.txt)" \
  -d target/bench ../../../docs/benchmarks/RateLimiterBench.java
java -cp "target/bench:target/classes:$(cat target/bench-cp.txt)" RateLimiterBench
```

On Windows PowerShell, replace `:` with `;`:

```powershell
$cp = "target\classes;" + (Get-Content target\bench-cp.txt -Raw).Trim()
javac -cp $cp -d target\bench ..\..\..\docs\benchmarks\RateLimiterBench.java
java -cp "target\bench;$cp" RateLimiterBench
```

## Interpretation

- A single-threaded limiter check costs roughly **62 ns**, which is noise
  compared with a database query, password hash, or network round trip.
- Four threads on distinct keys scale slightly better than one thread in this
  harness (JIT warming across threads), at about **54 ns/op aggregate**.
- Four threads on the same key serialize on the bucket, costing about
  **245 ns/op aggregate** — roughly 4× the single-thread cost. That is the
  number to care about if one hot key is hammered.
- These numbers say nothing about end-to-end HTTP latency. No p50/p95 HTTP
  delta has been measured, and none is claimed.

## Why this benchmark exists

The interview question was: "Show me the baseline latency delta." A limiter
microbenchmark is defensible; an invented HTTP p50/p95 is not. The honest
answer is: "The limiter itself is tens of nanoseconds per check; I have not
measured the full HTTP path, so I won't quote a p95."
