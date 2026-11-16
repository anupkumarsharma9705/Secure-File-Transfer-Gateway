package com.securetransfer.SFTG.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Per-IP throttling for the endpoints that are actually sensitive to abuse.
 *
 * Deliberately an ALLOWLIST of protected paths, not a denylist of exempt
 * ones. A denylist ("skip everything except X") silently under-protects
 * every new sensitive endpoint added later — that's exactly how
 * /api/auth/login shipped with zero rate limiting in the first place.
 *
 * This is the IP-level layer only: it stops one attacker hammering many
 * different accounts from one address. It does NOT stop one specific
 * account being targeted from many addresses — that's a separate,
 * per-account concern, handled where the identity is actually known
 * (AuthService's login lockout, UserService's OTP attempt limit), because
 * this filter runs before the request body is parsed and has no concept
 * of "which account" yet — only "which IP."
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private final RateLimitingService rateLimitingService;

    public RateLimitingFilter(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        RateLimit limit = RateLimit.forPath(request.getRequestURI());
        String key = limit.name() + ":ip:" + request.getRemoteAddr();

        if (rateLimitingService.tryConsume(key, limit.capacity(), limit.window())) {
            filterChain.doFilter(request, response);
        } else {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"error\":\"Too many requests\",\"message\":\"Please try again later.\"}");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // ✅ Allow CORS preflight through — never rate limit OPTIONS
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return RateLimit.forPath(request.getRequestURI()) == null;
    }

    /**
     * Named limit profiles, tuned per endpoint rather than one shared
     * default. Login/register/verify-otp are far tighter than file
     * downloads: guessing credentials or an OTP is a much higher-value
     * target for an attacker than download abuse, so the budget has to
     * be small enough to matter within the window that's actually
     * exploitable (an OTP is only good for 5 minutes — 10 IP-level
     * attempts in that window is already generous, the real backstop
     * is the per-account limit in UserService).
     */
    private enum RateLimit {
        SHARE_DOWNLOAD(5, Duration.ofMinutes(1)),
        LOGIN(15, Duration.ofMinutes(15)),
        REGISTER(10, Duration.ofMinutes(15)),
        VERIFY_OTP(10, Duration.ofMinutes(5));

        private final int capacity;
        private final Duration window;

        RateLimit(int capacity, Duration window) {
            this.capacity = capacity;
            this.window = window;
        }

        int capacity() { return capacity; }
        Duration window() { return window; }

        static RateLimit forPath(String uri) {
            if (uri.startsWith("/api/share/download")) return SHARE_DOWNLOAD;
            if (uri.equals("/api/auth/login")) return LOGIN;
            if (uri.equals("/api/auth/register")) return REGISTER;
            if (uri.equals("/api/auth/verify-otp")) return VERIFY_OTP;
            return null;
        }
    }
}