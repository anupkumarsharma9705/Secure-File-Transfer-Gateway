package com.securetransfer.SFTG.repository;

import com.securetransfer.SFTG.model.BlacklistedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

public interface BlacklistedTokenRepository
        extends JpaRepository<BlacklistedToken, Long> {

    // Called on every authenticated request — must be fast (uses index)
    boolean existsByToken(String token);

    // Called by the scheduler to purge expired tokens from DB
    @Modifying
    @Transactional
    @Query("DELETE FROM BlacklistedToken b WHERE b.expiresAt < :now")
    void deleteAllExpiredBefore(Instant now);
}