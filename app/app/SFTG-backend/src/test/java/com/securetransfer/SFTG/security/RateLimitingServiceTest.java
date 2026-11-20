package com.securetransfer.SFTG.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitingServiceTest {

    @Test
    void allowsRequestsUpToCapacity() {
        RateLimitingService service = new RateLimitingService();
        Duration window = Duration.ofMinutes(1);

        assertTrue(service.tryConsume("login:ip:1.2.3.4", 3, window));
        assertTrue(service.tryConsume("login:ip:1.2.3.4", 3, window));
        assertTrue(service.tryConsume("login:ip:1.2.3.4", 3, window));
    }

    @Test
    void rejectsRequestBeyondCapacity() {
        RateLimitingService service = new RateLimitingService();
        Duration window = Duration.ofMinutes(1);

        for (int i = 0; i < 5; i++) {
            assertTrue(service.tryConsume("share:ip:1.2.3.4", 5, window));
        }
        assertFalse(service.tryConsume("share:ip:1.2.3.4", 5, window));
    }

    @Test
    void bucketsAreIsolatedPerKey() {
        RateLimitingService service = new RateLimitingService();
        Duration window = Duration.ofMinutes(1);

        assertTrue(service.tryConsume("login:ip:1.1.1.1", 1, window));
        assertFalse(service.tryConsume("login:ip:1.1.1.1", 1, window));
        assertTrue(service.tryConsume("login:ip:2.2.2.2", 1, window));
    }

    @Test
    void probeExposesRemainingTokens() {
        RateLimitingService service = new RateLimitingService();
        Duration window = Duration.ofMinutes(1);

        var probe = service.tryConsumeAndReturnRemaining("probe:ip:1.2.3.4", 2, window);

        assertTrue(probe.isConsumed());
        assertEquals(1, probe.getRemainingTokens());
        assertTrue(probe.getNanosToWaitForReset() > 0);
    }

    @Test
    void refillsAfterWindow() throws InterruptedException {
        RateLimitingService service = new RateLimitingService();
        Duration window = Duration.ofMillis(200);

        assertTrue(service.tryConsume("otp:ip:1.2.3.4", 1, window));
        assertFalse(service.tryConsume("otp:ip:1.2.3.4", 1, window));
        Thread.sleep(250);
        assertTrue(service.tryConsume("otp:ip:1.2.3.4", 1, window));
    }
}
