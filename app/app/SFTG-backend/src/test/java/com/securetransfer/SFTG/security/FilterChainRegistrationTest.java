package com.securetransfer.SFTG.security;

import jakarta.servlet.Filter;
import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for two bugs found during a self-audit:
 *
 * 1. Both custom filters were {@code @Component} beans AND added to the
 *    security chain, so the servlet container registered them a second time.
 *    Each request therefore ran the rate limiter twice, silently halving
 *    every configured limit.
 * 2. Both filters were added "before UsernamePasswordAuthenticationFilter",
 *    which gives them the same order. Spring Security explicitly does not
 *    define an order for ties, so rate limiting was not guaranteed to run
 *    before JWT parsing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FilterChainRegistrationTest {

    @Autowired
    private ServletContext servletContext;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Test
    void customFiltersAreNotRegisteredTwiceByTheServletContainer() {
        assertFalse(servletContext.getFilterRegistrations().containsKey("jwtAuthenticationFilter"),
                "JwtAuthenticationFilter must only run inside the security chain");
        assertFalse(servletContext.getFilterRegistrations().containsKey("rateLimitingFilter"),
                "RateLimitingFilter must only run inside the security chain");
    }

    @Test
    void customFiltersAppearExactlyOnceInTheSecurityChain() {
        List<Filter> filters = securityChainFilters();

        assertEquals(1, filters.stream().filter(JwtAuthenticationFilter.class::isInstance).count(),
                "JwtAuthenticationFilter should be registered exactly once");
        assertEquals(1, filters.stream().filter(RateLimitingFilter.class::isInstance).count(),
                "RateLimitingFilter should be registered exactly once");
    }

    @Test
    void rateLimitingRunsBeforeJwtAuthentication() {
        List<Filter> filters = securityChainFilters();

        int rateLimitIndex = indexOf(filters, RateLimitingFilter.class);
        int jwtIndex = indexOf(filters, JwtAuthenticationFilter.class);

        assertTrue(rateLimitIndex >= 0, "RateLimitingFilter is missing from the security chain");
        assertTrue(jwtIndex >= 0, "JwtAuthenticationFilter is missing from the security chain");
        assertTrue(rateLimitIndex < jwtIndex,
                "RateLimitingFilter must run before JwtAuthenticationFilter");
    }

    private List<Filter> securityChainFilters() {
        return springSecurityFilterChain.getFilterChains().get(0).getFilters();
    }

    private int indexOf(List<Filter> filters, Class<?> type) {
        for (int i = 0; i < filters.size(); i++) {
            if (type.isInstance(filters.get(i))) {
                return i;
            }
        }
        return -1;
    }
}
