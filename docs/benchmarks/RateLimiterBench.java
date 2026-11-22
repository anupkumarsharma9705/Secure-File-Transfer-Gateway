import com.securetransfer.SFTG.security.RateLimitingService;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Small, dependency-free harness for the in-process Bucket4j limiter.
 *
 * It measures RateLimitingService.tryConsume only. It is NOT an HTTP latency
 * benchmark: there are no sockets, no Spring, and no database calls.
 *
 * Methodology:
 *   - 1,000,000 warm-up calls per scenario
 *   - 3 measured repetitions of 10,000,000 calls
 *   - median reported, plus min/max
 *   - capacity 1,000,000,000 with a 1-day refill so nothing is rejected
 *
 * See docs/benchmarks/README.md for the recorded results and run command.
 */
public final class RateLimiterBench {

    private static final int WARMUP = 1_000_000;
    private static final int OPS = 10_000_000;
    private static final int REPS = 3;
    private static final int CAPACITY = 1_000_000_000;
    private static final Duration WINDOW = Duration.ofDays(1);

    private static volatile boolean sink;

    public static void main(String[] args) throws Exception {
        runSingleKey();
        runMultiKey(false);
        runMultiKey(true);
    }

    private static void runSingleKey() throws Exception {
        System.out.println("scenario=1-thread-1-key");
        List<Double> nsPerOp = new ArrayList<>();
        for (int rep = 0; rep < REPS; rep++) {
            RateLimitingService service = new RateLimitingService();
            consume(service, "warm", WARMUP);
            long start = System.nanoTime();
            consume(service, "hot", OPS);
            long elapsed = System.nanoTime() - start;
            double value = (double) elapsed / OPS;
            nsPerOp.add(value);
            System.out.printf("  rep=%d total_ms=%.1f ns_per_op=%.2f ops_per_sec=%.0f%n",
                    rep + 1, elapsed / 1_000_000.0, value, 1_000_000_000.0 / value);
        }
        report(nsPerOp);
    }

    private static void runMultiKey(boolean sameKey) throws Exception {
        String label = sameKey ? "4-threads-1-key" : "4-threads-4-keys";
        System.out.println("scenario=" + label);
        List<Double> nsPerOp = new ArrayList<>();
        for (int rep = 0; rep < REPS; rep++) {
            RateLimitingService service = new RateLimitingService();
            runThreads(service, sameKey, WARMUP / 4);
            long start = System.nanoTime();
            long consumed = runThreads(service, sameKey, OPS / 4);
            long elapsed = System.nanoTime() - start;
            double value = (double) elapsed / consumed;
            nsPerOp.add(value);
            System.out.printf("  rep=%d total_ms=%.1f ns_per_op=%.2f ops_per_sec=%.0f%n",
                    rep + 1, elapsed / 1_000_000.0, value, 1_000_000_000.0 / value);
        }
        report(nsPerOp);
    }

    private static long runThreads(RateLimitingService service, boolean sameKey, int opsPerThread)
            throws InterruptedException {
        int threads = 4;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicLong consumed = new AtomicLong();

        for (int t = 0; t < threads; t++) {
            final int id = t;
            Thread thread = new Thread(() -> {
                String key = sameKey ? "shared" : "thread-" + id;
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                consumed.addAndGet(consume(service, key, opsPerThread));
                done.countDown();
            });
            thread.start();
        }

        ready.await();
        go.countDown();
        done.await();
        return consumed.get();
    }

    private static long consume(RateLimitingService service, String key, int ops) {
        long accepted = 0;
        for (int i = 0; i < ops; i++) {
            boolean ok = service.tryConsume(key, CAPACITY, WINDOW);
            sink = ok;
            if (ok) {
                accepted++;
            }
        }
        return accepted;
    }

    private static void report(List<Double> values) {
        Collections.sort(values);
        double median = values.get(values.size() / 2);
        System.out.printf("  median_ns_per_op=%.2f min=%.2f max=%.2f median_ops_per_sec=%.0f%n",
                median, values.get(0), values.get(values.size() - 1), 1_000_000_000.0 / median);
    }
}
