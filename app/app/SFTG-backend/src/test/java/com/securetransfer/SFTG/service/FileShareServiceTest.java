package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.exception.ForbiddenException;
import com.securetransfer.SFTG.exception.LinkExpiredException;
import com.securetransfer.SFTG.exception.ResourceNotFoundException;
import com.securetransfer.SFTG.model.FileEntity;
import com.securetransfer.SFTG.model.SharedLink;
import com.securetransfer.SFTG.model.User;
import com.securetransfer.SFTG.repository.FileRepository;
import com.securetransfer.SFTG.repository.SharedLinkRepository;
import com.securetransfer.SFTG.repository.UserActivityLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileShareServiceTest {

    @Mock
    private SharedLinkRepository sharedLinkRepository;

    @Mock
    private FileRepository fileRepository;

    @Mock
    private UserActivityLogRepository userActivityLogRepository;

    @InjectMocks
    private FileShareService service;

    private FileEntity file;

    @BeforeEach
    void setUp() {
        User owner = new User();
        owner.setEmail("owner@example.com");

        file = new FileEntity();
        file.setId(1L);
        file.setStoredFilename("stored.txt");
        file.setOriginalFilename("original.txt");
        file.setUploadedBy(owner);
    }

    @Test
    void generateShareLinkPersistsOneHourFiveDownloadLink() {
        when(fileRepository.findByStoredFilename("stored.txt")).thenReturn(Optional.of(file));

        String token = service.generateShareLink("stored.txt", "owner@example.com");

        assertNotNull(token);
        ArgumentCaptor<SharedLink> captor = ArgumentCaptor.forClass(SharedLink.class);
        verify(sharedLinkRepository).save(captor.capture());
        SharedLink saved = captor.getValue();
        assertEquals(token, saved.getToken());
        assertEquals(5, saved.getDownloadLimit());
        assertTrue(saved.isActive());
        assertSame(file, saved.getFile());
        assertTrue(saved.getExpiresAt().isAfter(Instant.now().plus(59, ChronoUnit.MINUTES)));
        verify(userActivityLogRepository).save(any());
    }

    @Test
    void generateShareLinkFailsWhenFileMissing() {
        when(fileRepository.findByStoredFilename("missing.txt")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.generateShareLink("missing.txt", "owner@example.com"));
    }

    @Test
    void generateShareLinkFailsForNonOwner() {
        when(fileRepository.findByStoredFilename("stored.txt")).thenReturn(Optional.of(file));
        assertThrows(ForbiddenException.class,
                () -> service.generateShareLink("stored.txt", "intruder@example.com"));
        verify(sharedLinkRepository, never()).save(any());
    }

    @Test
    void validateLinkRejectsBlankToken() {
        assertThrows(IllegalArgumentException.class, () -> service.validateLink(" "));
    }

    @Test
    void validateLinkRejectsMalformedToken() {
        assertThrows(IllegalArgumentException.class, () -> service.validateLink("not-a-uuid"));
    }

    @Test
    void validateLinkRejectsUnknownToken() {
        String token = UUID.randomUUID().toString();
        when(sharedLinkRepository.findByTokenAndActiveTrue(token)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.validateLink(token));
    }

    @Test
    void validateLinkDeactivatesExpiredLink() {
        String token = UUID.randomUUID().toString();
        SharedLink link = activeLink(token);
        link.setExpiresAt(Instant.now().minusSeconds(60));
        when(sharedLinkRepository.findByTokenAndActiveTrue(token)).thenReturn(Optional.of(link));

        assertThrows(LinkExpiredException.class, () -> service.validateLink(token));
        assertFalse(link.isActive());
        verify(sharedLinkRepository).save(link);
    }

    @Test
    void validateLinkRejectsExhaustedDownloadLimit() {
        String token = UUID.randomUUID().toString();
        SharedLink link = activeLink(token);
        link.setDownloadCount(5);
        link.setDownloadLimit(5);
        when(sharedLinkRepository.findByTokenAndActiveTrue(token)).thenReturn(Optional.of(link));

        assertThrows(ForbiddenException.class, () -> service.validateLink(token));
        assertFalse(link.isActive());
        verify(sharedLinkRepository).save(link);
    }

    @Test
    void validateLinkIncrementsDownloadCount() {
        String token = UUID.randomUUID().toString();
        SharedLink link = activeLink(token);
        when(sharedLinkRepository.findByTokenAndActiveTrue(token)).thenReturn(Optional.of(link));

        SharedLink validated = service.validateLink(token);

        assertEquals(1, validated.getDownloadCount());
        verify(sharedLinkRepository).save(link);
    }

    @Test
    void validateAndGetFileFailsWhenFileRowMissing() {
        String token = UUID.randomUUID().toString();
        SharedLink link = activeLink(token);
        when(sharedLinkRepository.findByTokenAndActiveTrue(token)).thenReturn(Optional.of(link));
        when(fileRepository.findByStoredFilename("stored.txt")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.validateAndGetFile(token));
    }

    private SharedLink activeLink(String token) {
        return SharedLink.builder()
                .token(token)
                .storedFilename("stored.txt")
                .originalFilename("original.txt")
                .createdByUsername("owner@example.com")
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .downloadLimit(5)
                .downloadCount(0)
                .active(true)
                .file(file)
                .build();
    }
}
