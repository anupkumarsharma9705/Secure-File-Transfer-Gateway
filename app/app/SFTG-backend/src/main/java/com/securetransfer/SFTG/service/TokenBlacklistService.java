package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.model.BlacklistedToken;
import com.securetransfer.SFTG.repository.BlacklistedTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service                  // Only @Service, removed duplicate @Component
@RequiredArgsConstructor
@Slf4j
public class TokenBlacklistService {

    private final BlacklistedTokenRepository blacklistedTokenRepository;

    /**
     * Called on logout.
     * Saves the token to DB so it stays blacklisted even after server restart.
     */
    public void blacklistToken(String token, Instant expiresAt, String userEmail) {
        BlacklistedToken blacklistedToken = BlacklistedToken.builder()
                .token(token)
                .expiresAt(expiresAt)
                .blacklistedAt(Instant.now())
                .blacklistedByEmail(userEmail)
                .build();

        blacklistedTokenRepository.save(blacklistedToken);
        log.info("[SECURITY] Token blacklisted for user: {}", userEmail);
    }

    /**
     * Called on every authenticated request by JwtAuthenticationFilter.
     * existsByToken uses the DB index — fast lookup.
     */
    public boolean isBlacklisted(String token) {
        return blacklistedTokenRepository.existsByToken(token);
    }

    /**
     * Runs every hour.
     * Deletes tokens whose JWT expiry has already passed —
     * they can no longer be used anyway, so no need to keep them in DB.
     * This keeps the blacklisted_tokens table from growing forever.
     */
    @Scheduled(fixedRate = 60 * 60 * 1000) // every 1 hour
    public void cleanupExpiredTokens() {
        blacklistedTokenRepository.deleteAllExpiredBefore(Instant.now());
        log.info("[SCHEDULER] Expired blacklisted tokens cleaned up from DB");
    }
}