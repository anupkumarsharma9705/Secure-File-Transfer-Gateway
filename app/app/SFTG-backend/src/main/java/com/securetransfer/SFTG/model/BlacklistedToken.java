package com.securetransfer.SFTG.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(
        name = "blacklisted_tokens",
        indexes = {
                // Index on token for fast lookup on every request
                @Index(name = "idx_blacklisted_token", columnList = "token"),
                // Index on expiresAt for fast cleanup queries
                @Index(name = "idx_blacklisted_expires_at", columnList = "expiresAt")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Full JWT string — length 512 covers standard JWT sizes
    @Column(nullable = false, unique = true, length = 512)
    private String token;

    // When the JWT naturally expires — used by the cleanup scheduler
    @Column(nullable = false)
    private Instant expiresAt;

    // When we blacklisted it (i.e. when logout happened)
    @Column(nullable = false)
    private Instant blacklistedAt;

    // Who triggered the logout — useful for audit
    @Column
    private String blacklistedByEmail;
}