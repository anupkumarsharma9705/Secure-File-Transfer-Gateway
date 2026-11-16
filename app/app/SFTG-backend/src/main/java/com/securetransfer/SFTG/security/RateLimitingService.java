package com.securetransfer.SFTG.security;

import io.github.bucket4j.*;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingService {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Tries to consume one token from the bucket for this key, creating the
     * bucket on first use with the given capacity/refill window. Different
     * callers can use different limits for the same underlying key space —
     * a brute-force-sensitive endpoint (login, OTP) needs a much tighter
     * budget than a general-purpose one (file download), so the limit is
     * a parameter, not a single hardcoded default shared by everyone.
     */
    public boolean tryConsume(String key, int capacity, Duration refillPeriod) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> newBucket(capacity, refillPeriod));
        return bucket.tryConsume(1);
    }

    private Bucket newBucket(int capacity, Duration refillPeriod) {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.intervally(capacity, refillPeriod)))
                .build();
    }
}