package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.model.BlacklistedToken;
import com.securetransfer.SFTG.repository.BlacklistedTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private BlacklistedTokenRepository repository;

    @InjectMocks
    private TokenBlacklistService service;

    @Test
    void blacklistTokenPersistsTokenWithMetadata() {
        Instant expiry = Instant.now().plusSeconds(3600);

        service.blacklistToken("jwt-token", expiry, "user@example.com");

        ArgumentCaptor<BlacklistedToken> captor = ArgumentCaptor.forClass(BlacklistedToken.class);
        verify(repository).save(captor.capture());
        BlacklistedToken saved = captor.getValue();
        assertEquals("jwt-token", saved.getToken());
        assertEquals(expiry, saved.getExpiresAt());
        assertEquals("user@example.com", saved.getBlacklistedByEmail());
        assertNotNull(saved.getBlacklistedAt());
    }

    @Test
    void isBlacklistedDelegatesToRepository() {
        when(repository.existsByToken("jwt-token")).thenReturn(true);
        assertTrue(service.isBlacklisted("jwt-token"));

        when(repository.existsByToken("other")).thenReturn(false);
        assertFalse(service.isBlacklisted("other"));
    }

    @Test
    void cleanupDeletesOnlyExpiredTokens() {
        service.cleanupExpiredTokens();
        verify(repository).deleteAllExpiredBefore(any(Instant.class));
        verifyNoMoreInteractions(repository);
    }
}
