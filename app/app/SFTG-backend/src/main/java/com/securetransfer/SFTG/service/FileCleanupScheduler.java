package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.model.FileEntity;
import com.securetransfer.SFTG.repository.DownloadLogRepository;
import com.securetransfer.SFTG.repository.FileRepository;
import com.securetransfer.SFTG.repository.SharedLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class FileCleanupScheduler {

    private final FileRepository fileRepository;
    private final SharedLinkRepository sharedLinkRepository;
    private final DownloadLogRepository downloadLogRepository;

    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void deleteExpiredFiles() {
        List<FileEntity> expiredFiles =
                fileRepository.findByExpiryAtBefore(Instant.now());

        for (FileEntity file : expiredFiles) {
            try {
                // Step 1 — delete download_logs that belong to this file's shared links
                sharedLinkRepository
                        .findAllByFile(file)
                        .forEach(link -> {
                            downloadLogRepository.deleteBySharedLink(link);
                        });

                // Step 2 — delete shared_links for this file
                sharedLinkRepository.deleteByFile(file);

                // Step 3 — delete physical file from disk
                Files.deleteIfExists(Paths.get(file.getFilePath()));

                // Step 4 — delete file metadata from DB
                fileRepository.delete(file);

                log.info("[CLEANUP] Deleted expired file: {}", file.getOriginalFilename());

            } catch (Exception e) {
                log.error("[CLEANUP] Failed to delete file: {}", file.getFilePath(), e);
            }
        }
    }
}