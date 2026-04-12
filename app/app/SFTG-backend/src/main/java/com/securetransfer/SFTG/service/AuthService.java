package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.dto.AuthRequest;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

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

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!user.isEmailVerified()) {
            throw new RuntimeException("Email not verified. Please verify your email first.");
        }

        // Spring Security validates credentials — throws BadCredentialsException if wrong
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

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