package com.securetransfer.SFTG.controller;

import com.securetransfer.SFTG.dto.*;
import com.securetransfer.SFTG.model.UserActivityLog;
import com.securetransfer.SFTG.repository.UserActivityLogRepository;
import com.securetransfer.SFTG.security.JwtTokenUtil;
import com.securetransfer.SFTG.service.AuthService;
import com.securetransfer.SFTG.service.TokenBlacklistService;
import com.securetransfer.SFTG.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Date;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor   // Only ONE injection strategy — constructor injection
@Slf4j
public class AuthController {

    private final UserService userService;
    private final AuthService authService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtTokenUtil jwtTokenUtil;
    private final UserActivityLogRepository userActivityLogRepository;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(request);
        return ResponseEntity.ok("OTP sent to email");
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<String> verifyOtp(@RequestBody OtpRequest request) {
        userService.verifyOtp(request);
        return ResponseEntity.ok("Email verified successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @RequestBody AuthRequest request,
            HttpServletRequest httpRequest) {  // ← pass request to service for IP/UA

        String token = authService.login(request, httpRequest);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            HttpServletRequest request,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("You are not logged in");
        }

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Date expiryDate = jwtTokenUtil.getExpirationDateFromToken(token);

            // blacklistToken now takes 3 args: token, expiry, email
            tokenBlacklistService.blacklistToken(
                    token,
                    expiryDate.toInstant(),
                    authentication.getName()   // ← added
            );
        }

        userActivityLogRepository.save(
                UserActivityLog.builder()
                        .userEmail(authentication.getName())
                        .action("LOGOUT")
                        .timestamp(Instant.now())
                        .ipAddress(extractClientIp(request))    // ← now captured
                        .userAgent(request.getHeader("User-Agent")) // ← now captured
                        .build()
        );

        log.info("[AUTH] Logout successful for user: {}", authentication.getName());
        return ResponseEntity.ok("Logged out successfully");
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}