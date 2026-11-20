package com.securetransfer.SFTG.security;

import com.securetransfer.SFTG.model.Role;
import com.securetransfer.SFTG.model.User;
import com.securetransfer.SFTG.repository.UserRepository;
import com.securetransfer.SFTG.service.TokenBlacklistService;
import io.github.bucket4j.ConsumptionProbe;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ---------------- JwtAuthenticationFilter ----------------

    private JwtAuthenticationFilter jwtFilter(
            UserDetailsServiceImpl userDetailsService,
            JwtTokenUtil jwtTokenUtil,
            TokenBlacklistService tokenBlacklistService) {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
        ReflectionTestUtils.setField(filter, "jwtTokenUtil", jwtTokenUtil);
        ReflectionTestUtils.setField(filter, "tokenBlacklistService", tokenBlacklistService);
        return filter;
    }

    @Test
    void jwtFilterSkipsPublicPaths() throws Exception {
        JwtAuthenticationFilter filter = jwtFilter(
                mock(UserDetailsServiceImpl.class), mock(JwtTokenUtil.class), mock(TokenBlacklistService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setServletPath("/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void jwtFilterLetsOptionsThrough() throws Exception {
        JwtAuthenticationFilter filter = jwtFilter(
                mock(UserDetailsServiceImpl.class), mock(JwtTokenUtil.class), mock(TokenBlacklistService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/files/my-files");
        request.setServletPath("/api/files/my-files");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void jwtFilterContinuesWithoutToken() throws Exception {
        JwtAuthenticationFilter filter = jwtFilter(
                mock(UserDetailsServiceImpl.class), mock(JwtTokenUtil.class), mock(TokenBlacklistService.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/my-files");
        request.setServletPath("/api/files/my-files");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void jwtFilterAuthenticatesValidToken() throws Exception {
        UserDetailsServiceImpl userDetailsService = mock(UserDetailsServiceImpl.class);
        JwtTokenUtil jwtTokenUtil = mock(JwtTokenUtil.class);
        TokenBlacklistService blacklist = mock(TokenBlacklistService.class);
        JwtAuthenticationFilter filter = jwtFilter(userDetailsService, jwtTokenUtil, blacklist);

        UserDetails details = org.springframework.security.core.userdetails.User
                .withUsername("user@example.com").password("pw").authorities("ROLE_USER").build();
        when(jwtTokenUtil.getUsernameFromToken("token")).thenReturn("user@example.com");
        when(blacklist.isBlacklisted("token")).thenReturn(false);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(details);
        when(jwtTokenUtil.validateToken("token", details)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/my-files");
        request.setServletPath("/api/files/my-files");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("user@example.com",
                SecurityContextHolder.getContext().getAuthentication().getName());
        verify(chain).doFilter(request, response);
    }

    @Test
    void jwtFilterRejectsBlacklistedToken() throws Exception {
        JwtTokenUtil jwtTokenUtil = mock(JwtTokenUtil.class);
        TokenBlacklistService blacklist = mock(TokenBlacklistService.class);
        JwtAuthenticationFilter filter = jwtFilter(
                mock(UserDetailsServiceImpl.class), jwtTokenUtil, blacklist);
        when(jwtTokenUtil.getUsernameFromToken("token")).thenReturn("user@example.com");
        when(blacklist.isBlacklisted("token")).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/my-files");
        request.setServletPath("/api/files/my-files");
        request.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void jwtFilterContinuesAfterExpiredOrMalformedToken() throws Exception {
        JwtTokenUtil jwtTokenUtil = mock(JwtTokenUtil.class);
        when(jwtTokenUtil.getUsernameFromToken("expired"))
                .thenThrow(new ExpiredJwtException(null, null, "expired"));
        when(jwtTokenUtil.getUsernameFromToken("malformed"))
                .thenThrow(new MalformedJwtException("bad token"));

        JwtAuthenticationFilter filter = jwtFilter(
                mock(UserDetailsServiceImpl.class), jwtTokenUtil, mock(TokenBlacklistService.class));

        for (String token : new String[]{"expired", "malformed"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files/my-files");
            request.setServletPath("/api/files/my-files");
            request.addHeader("Authorization", "Bearer " + token);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
            assertNull(SecurityContextHolder.getContext().getAuthentication());
        }
    }

    // ---------------- RateLimitingFilter ----------------

    @Test
    void rateLimitAllowsRequestUnderCapacity() throws Exception {
        RateLimitingService service = mock(RateLimitingService.class);
        when(service.tryConsumeAndReturnRemaining(anyString(), anyInt(), any()))
                .thenReturn(ConsumptionProbe.consumed(14, TimeUnit.MINUTES.toNanos(15)));
        RateLimitingFilter filter = new RateLimitingFilter(service);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertEquals("15", response.getHeader("X-RateLimit-Limit"));
        assertEquals("14", response.getHeader("X-RateLimit-Remaining"));
        assertEquals("900", response.getHeader("X-RateLimit-Reset"));
    }

    @Test
    void rateLimitReturns429WhenExhausted() throws Exception {
        RateLimitingService service = mock(RateLimitingService.class);
        when(service.tryConsumeAndReturnRemaining(anyString(), anyInt(), any()))
                .thenReturn(ConsumptionProbe.rejected(
                        0, TimeUnit.SECONDS.toNanos(30), TimeUnit.MINUTES.toNanos(15)));
        RateLimitingFilter filter = new RateLimitingFilter(service);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(429, response.getStatus());
        assertTrue(response.getContentAsString().contains("Too many requests"));
        assertEquals("15", response.getHeader("X-RateLimit-Limit"));
        assertEquals("0", response.getHeader("X-RateLimit-Remaining"));
        assertEquals("900", response.getHeader("X-RateLimit-Reset"));
        assertEquals("30", response.getHeader("Retry-After"));
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void rateLimitSkipsOptionsAndUnprotectedPaths() throws Exception {
        RateLimitingService service = mock(RateLimitingService.class);
        RateLimitingFilter filter = new RateLimitingFilter(service);

        MockHttpServletRequest options = new MockHttpServletRequest("OPTIONS", "/api/auth/login");
        MockHttpServletResponse optionsResponse = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(options, optionsResponse, chain);
        verify(chain).doFilter(options, optionsResponse);

        MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/files/my-files");
        MockHttpServletResponse otherResponse = new MockHttpServletResponse();
        FilterChain otherChain = mock(FilterChain.class);
        filter.doFilter(other, otherResponse, otherChain);
        verify(otherChain).doFilter(other, otherResponse);

        verify(service, never()).tryConsumeAndReturnRemaining(anyString(), anyInt(), any());
    }

    // ---------------- UserDetailsServiceImpl ----------------

    @Test
    void userDetailsServiceMapsRoles() {
        UserRepository repository = mock(UserRepository.class);
        User user = new User();
        user.setEmail("user@example.com");
        user.setPassword("hashed");
        user.setRoles(Set.of(Role.ROLE_USER, Role.ROLE_ADMIN));
        when(repository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        UserDetailsServiceImpl service = new UserDetailsServiceImpl();
        ReflectionTestUtils.setField(service, "userRepository", repository);

        UserDetails details = service.loadUserByUsername("user@example.com");

        assertEquals("user@example.com", details.getUsername());
        assertEquals("hashed", details.getPassword());
        assertEquals(2, details.getAuthorities().size());
    }

    @Test
    void userDetailsServiceThrowsForUnknownUser() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        UserDetailsServiceImpl service = new UserDetailsServiceImpl();
        ReflectionTestUtils.setField(service, "userRepository", repository);

        assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("ghost@example.com"));
    }

    // ---------------- JwtAuthenticationEntryPoint ----------------

    @Test
    void entryPointReturns401() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationEntryPoint().commence(
                new MockHttpServletRequest(), response,
                new InsufficientAuthenticationException("no credentials"));

        assertEquals(401, response.getStatus());
    }
}
