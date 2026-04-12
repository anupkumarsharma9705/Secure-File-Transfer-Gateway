package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.model.UserActivityLog;
import com.securetransfer.SFTG.exception.ForbiddenException;
import com.securetransfer.SFTG.exception.LinkExpiredException;
import com.securetransfer.SFTG.exception.ResourceNotFoundException;
import com.securetransfer.SFTG.model.FileEntity;
import com.securetransfer.SFTG.model.SharedLink;
import com.securetransfer.SFTG.repository.FileRepository;
import com.securetransfer.SFTG.repository.SharedLinkRepository;
import com.securetransfer.SFTG.repository.UserActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FileShareService {

    private final SharedLinkRepository sharedLinkRepository;
    private final FileRepository fileRepository;
    private final UserActivityLogRepository userActivityLogRepository;

    public String generateShareLink(String storedFilename, String username) {

        FileEntity file = fileRepository.findByStoredFilename(storedFilename)
                .orElseThrow(() ->
                        new ResourceNotFoundException("File not found")
                );

        if (!file.getUploadedBy().getEmail().equals(username)) {
            throw new ForbiddenException("You cannot share a file you do not own");
        }

        SharedLink link = SharedLink.builder()
                .token(UUID.randomUUID().toString())
                .storedFilename(file.getStoredFilename())
                .originalFilename(file.getOriginalFilename())
                .createdByUsername(username)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .downloadLimit(5)
                .active(true)
                .file(file)          // ✅ ADD THIS LINE
                .build();

        sharedLinkRepository.save(link);

        userActivityLogRepository.save(
                UserActivityLog.builder()
                        .userEmail(username)
                        .action("LINK_GENERATE")
                        .timestamp(Instant.now())
                        .metadata(storedFilename)
                        .build()
        );

        return link.getToken();
    }

    public SharedLink validateLink(String token) {

        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be empty");
        }

        if (!token.matches("^[a-fA-F0-9\\-]{36}$")) {
            throw new IllegalArgumentException("Invalid token format");
        }

        SharedLink link = sharedLinkRepository.findByTokenAndActiveTrue(token)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Invalid or inactive link")
                );

        if (link.getExpiresAt().isBefore(Instant.now())) {
            link.setActive(false);
            sharedLinkRepository.save(link);
            throw new LinkExpiredException("Link expired");
        }

        if (link.getDownloadCount() >= link.getDownloadLimit()) {
            link.setActive(false);
            sharedLinkRepository.save(link);
            throw new ForbiddenException("Download limit exceeded");
        }

        link.setDownloadCount(link.getDownloadCount() + 1);
        sharedLinkRepository.save(link);

        return link;
    }

    public FileEntity validateAndGetFile(String token) {
        SharedLink link = validateLink(token);

        return fileRepository.findByStoredFilename(link.getStoredFilename())
                .orElseThrow(() ->
                        new ResourceNotFoundException("File not found")
                );
    }
}
