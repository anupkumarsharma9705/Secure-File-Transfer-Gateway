// src/main/java/com/securetransfer/SFTG/service/UserService.java
package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.dto.OtpRequest;
import com.securetransfer.SFTG.dto.RegisterRequest;
import com.securetransfer.SFTG.exception.AccountLockedException;
import com.securetransfer.SFTG.model.Role;
import com.securetransfer.SFTG.model.User;
import com.securetransfer.SFTG.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Set;

@Service
public class UserService {

    // Mirrors AuthService's MAX_FAILED_LOGIN_ATTEMPTS pattern, applied to
    // the endpoint that actually needed it: an OTP is only 6 digits
    // (900,000 combinations) and lives for 5 minutes — with zero attempt
    // limiting that window is realistically brute-forceable. Once attempts
    // are exhausted we invalidate the code outright rather than time-locking
    // the account, since the OTP already expires on its own — the honest
    // next step for the user is "request a new code," not "wait it out."
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;
    public User register(RegisterRequest request) {

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);

        // CASE 1: Email exists & already verified → block registration
        if (user != null && user.isEmailVerified()) {
            throw new RuntimeException("Account already exists. Please login.");
        }

        // Generate OTP
        String otp = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));
        long expiryTime = System.currentTimeMillis() + 5 * 60 * 1000;

        // CASE 2: Email exists but NOT verified → resend OTP
        if (user != null && !user.isEmailVerified()) {

            user.setOtp(otp);
            user.setOtpExpiryTime(expiryTime);
            user.setOtpAttempts(0); // fresh code gets a fresh attempt budget
            userRepository.save(user);

            emailService.sendOtp(user.getEmail(), otp);
            return user;
        }

        // CASE 3: New email → create new user
        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setEmailVerified(false);
        newUser.setRoles(Set.of(Role.ROLE_USER));
        newUser.setOtp(otp);
        newUser.setOtpExpiryTime(expiryTime);
        newUser.setCreatedAt(Instant.now());  // ← ADD THIS LINE
        newUser.setActive(true);              // ← ADD THIS LINE (it has @Column nullable=false)

        userRepository.save(newUser);
        emailService.sendOtp(newUser.getEmail(), otp);

        return newUser;
    }

    public void verifyOtp(OtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.isEmailVerified()) {
            throw new RuntimeException("Already verified");
        }

        if (!request.getOtp().equals(user.getOtp())) {
            registerFailedOtpAttempt(user);
            throw new RuntimeException("Invalid OTP");
        }

        if (user.getOtpExpiryTime() < System.currentTimeMillis()) {
            throw new RuntimeException("OTP expired");
        }

        user.setEmailVerified(true);
        user.setOtp(null);
        user.setOtpExpiryTime(null);
        user.setOtpAttempts(0);
        userRepository.save(user);
    }

    private void registerFailedOtpAttempt(User user) {
        int attempts = user.getOtpAttempts() + 1;
        user.setOtpAttempts(attempts);

        if (attempts >= MAX_OTP_ATTEMPTS) {
                // Burn the code rather than lock the account — there's nothing
            // left worth protecting by waiting, since this OTP can never
            // succeed again either way.
            user.setOtp(null);
            user.setOtpExpiryTime(null);
            user.setOtpAttempts(0);
            userRepository.save(user);
            throw new AccountLockedException(
                    "Too many incorrect codes. Please request a new one.");
        }

        userRepository.save(user);
    }
}