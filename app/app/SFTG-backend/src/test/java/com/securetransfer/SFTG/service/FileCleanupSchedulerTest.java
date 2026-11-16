package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.model.FileEntity;
import com.securetransfer.SFTG.model.SharedLink;
import com.securetransfer.SFTG.repository.DownloadLogRepository;
import com.securetransfer.SFTG.repository.FileRepository;
import com.securetransfer.SFTG.repository.SharedLinkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileCleanupSchedulerTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private SharedLinkRepository sharedLinkRepository;

    @Mock
    private DownloadLogRepository downloadLogRepository;

    @InjectMocks
    private FileCleanupScheduler scheduler;

    @Test
    void deletesExpiredFilesInForeignKeySafeOrder() {
        FileEntity file = new FileEntity();
        file.setId(1L);
        file.setOriginalFilename("expired.txt");
        file.setFilePath("target/does-not-exist.txt");

        SharedLink link = SharedLink.builder()
                .token("token")
                .storedFilename("expired.txt")
                .originalFilename("expired.txt")
                .createdByUsername("user@example.com")
                .expiresAt(Instant.now().minusSeconds(60))
                .downloadLimit(5)
                .downloadCount(0)
                .active(false)
                .file(file)
                .build();

        when(fileRepository.findByExpiryAtBefore(any(Instant.class))).thenReturn(List.of(file));
        when(sharedLinkRepository.findAllByFile(file)).thenReturn(List.of(link));

        scheduler.deleteExpiredFiles();

        InOrder order = inOrder(downloadLogRepository, sharedLinkRepository, fileRepository);
        order.verify(downloadLogRepository).deleteBySharedLink(link);
        order.verify(sharedLinkRepository).deleteByFile(file);
        order.verify(fileRepository).delete(file);
        verify(sharedLinkRepository).deleteByFile(file);
    }

    @Test
    void doesNothingWhenNoFilesExpired() {
        when(fileRepository.findByExpiryAtBefore(any(Instant.class))).thenReturn(List.of());

        scheduler.deleteExpiredFiles();

        verifyNoInteractions(downloadLogRepository);
        verifyNoInteractions(sharedLinkRepository);
        verify(fileRepository, never()).delete(any());
    }
}
