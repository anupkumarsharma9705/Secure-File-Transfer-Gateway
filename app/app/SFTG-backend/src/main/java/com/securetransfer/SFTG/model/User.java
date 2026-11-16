// src/main/java/com/securetransfer/SFTG/model/User.java
package com.securetransfer.SFTG.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private boolean emailVerified = false;

    @Column(nullable = false)
    private String password;

    @Column
    private String otp;

    @Column
    private Long otpExpiryTime;

    // Wired up by UserService.registerFailedOtpAttempt() — invalidates the
    // OTP after MAX_OTP_ATTEMPTS wrong guesses instead of allowing unlimited
    // attempts against a 6-digit, 5-minute-lived code.
    @Column(nullable = false)
    private int otpAttempts = 0;

    // Wired up by AuthService.registerFailedLoginAttempt() — the
    // per-account half of login protection (RateLimitingFilter covers
    // the per-IP half).
    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    @Column
    private Instant accountLockedUntil;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column
    private Instant lastLogoutAt;
}