package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.dto.AuthRequest;
import com.securetransfer.SFTG.exception.AccountLockedException;
import com.securetransfer.SFTG.exception.ForbiddenException;
import com.securetransfer.SFTG.model.UserActivityLog;
import com.securetransfer.SFTG.model.User;
import com.securetransfer.SFTG.repository.UserActivityLogRepository;
import com.securetransfer.SFTG.repository.UserRepository;
import com.securetransfer.SFTG.security.JwtTokenUtil;
import com.securetransfer.SFTG.security.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    // Same shape as UserService's OTP attempt limit — this is the
    // per-account half of "per-IP AND per-account" for login. The
    // per-IP half already exists in RateLimitingFilter.
    private static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    private static final Duration LOGIN_LOCKOUT_DURATION = Duration.ofMinutes(15);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final UserDetailsServiceImpl userDetailsService;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserActivityLogRepository userActivityLogRepository;

    /**
     * HttpServletRequest is passed from controller — service should not
     * know about HTTP directly, but we need IP/UA for audit logging.
     * This is an accepted pattern for audit-focused services.
     */
    public String login(AuthRequest request, HttpServletRequest httpRequest) {

        // "Not found" is deliberately the SAME exception, status, and
        // message as a wrong password below — an attacker probing emails
        // directly against the API must not be able to tell the two apart.
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Checked BEFORE attempting credentials: a correct password doesn't
        // lift an active lock early, and we avoid spending a real auth
        // check on an account we're rejecting regardless of the outcome.
        if (user.getAccountLockedUntil() != null
                && Instant.now().isBefore(user.getAccountLockedUntil())) {
            throw new AccountLockedException(
                    "Account temporarily locked due to repeated failed login attempts. " +
                            "Please try again later.");
        }

        try {
            // Spring Security validates credentials — throws BadCredentialsException if wrong
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (BadCredentialsException ex) {
            registerFailedLoginAttempt(user);
            throw ex; // still maps to 401 — unless this attempt just tripped the lock above
        }

        // Successful login clears any accumulated failed attempts.
        if (user.getFailedLoginAttempts() != 0 || user.getAccountLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setAccountLockedUntil(null);
            userRepository.save(user);
        }

        // Only reachable with a CORRECT password now — an attacker without
        // the real password can no longer use this response to learn
        // whether an account exists and is simply unverified.
        if (!user.isEmailVerified()) {
            throw new ForbiddenException("Email not verified. Please verify your email first.");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());

        // Capture audit data
        String ipAddress = extractClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        userActivityLogRepository.save(
                UserActivityLog.builder()
                        .userEmail(request.getEmail())
                        .action("LOGIN")
                        .timestamp(Instant.now())
                        .ipAddress(ipAddress)
                        .userAgent(userAgent)
                        .build()
        );

        log.info("[AUTH] Login successful for user: {} from IP: {}", request.getEmail(), ipAddress);

        return jwtTokenUtil.generateToken(userDetails);
    }

    private void registerFailedLoginAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        if (attempts >= MAX_FAILED_LOGIN_ATTEMPTS) {
            user.setAccountLockedUntil(Instant.now().plus(LOGIN_LOCKOUT_DURATION));
            user.setFailedLoginAttempts(0);
            userRepository.save(user);
            throw new AccountLockedException(
                    "Too many failed login attempts. Account locked for 15 minutes.");
        }

        userRepository.save(user);
    }

    /**
     * Checks X-Forwarded-For header first (handles reverse proxies like Nginx).
     * Falls back to direct remote address.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For can be a comma-separated list; first one is the real client
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}