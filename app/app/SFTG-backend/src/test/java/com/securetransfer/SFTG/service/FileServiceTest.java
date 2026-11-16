package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.dto.FileDetailsResponseDTO;
import com.securetransfer.SFTG.exception.FileStorageException;
import com.securetransfer.SFTG.exception.ResourceNotFoundException;
import com.securetransfer.SFTG.model.DownloadLog;
import com.securetransfer.SFTG.model.FileEntity;
import com.securetransfer.SFTG.model.SharedLink;
import com.securetransfer.SFTG.model.User;
import com.securetransfer.SFTG.repository.DownloadLogRepository;
import com.securetransfer.SFTG.repository.FileRepository;
import com.securetransfer.SFTG.repository.SharedLinkRepository;
import com.securetransfer.SFTG.repository.UserActivityLogRepository;
import com.securetransfer.SFTG.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileServiceTest {

    @Mock private FileRepository fileRepository;
    @Mock private SharedLinkRepository sharedLinkRepository;
    @Mock private DownloadLogRepository downloadLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserActivityLogRepository userActivityLogRepository;
    @Mock private VirusTotalService virusTotalService;

    @InjectMocks private FileService service;

    @TempDir Path tempDir;

    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
        service.init();

        user = new User();
        user.setEmail("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(fileRepository.save(any(FileEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void rejectsFileOverSizeLimit() {
        org.springframework.web.multipart.MultipartFile file =
                mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.getSize()).thenReturn(101L * 1024 * 1024);
        when(file.getOriginalFilename()).thenReturn("big.txt");

        assertThrows(FileStorageException.class,
                () -> service.storeFile(file, "user@example.com"));
    }

    @Test
    void rejectsPathTraversalFilename() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "../evil.txt", "text/plain", "hi".getBytes(StandardCharsets.UTF_8));

        assertThrows(FileStorageException.class,
                () -> service.storeFile(file, "user@example.com"));
    }

    @Test
    void storesFileAndMetadataWithOneHourExpiry() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain", "hello world".getBytes(StandardCharsets.UTF_8));

        FileEntity stored = service.storeFile(file, "user@example.com");

        assertNotNull(stored.getStoredFilename());
        assertTrue(stored.getStoredFilename().endsWith("_hello.txt"));
        assertEquals("hello.txt", stored.getOriginalFilename());
        assertEquals("user@example.com", stored.getUploadedBy().getEmail());
        assertNotNull(stored.getExpiryAt());
        assertTrue(stored.getFilePath().contains("_hello.txt"));
        assertTrue(Files.exists(Path.of(stored.getFilePath())));
        verify(virusTotalService).scanFile(any(Path.class));
    }

    @Test
    void deletesFileAndFailsWhenScannerFlagsIt() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bad.txt", "text/plain", "bad".getBytes(StandardCharsets.UTF_8));
        doThrow(new FileStorageException("Malicious file detected"))
                .when(virusTotalService).scanFile(any(Path.class));

        Long before = countFiles(tempDir);
        assertThrows(FileStorageException.class,
                () -> service.storeFile(file, "user@example.com"));
        assertEquals(before, countFiles(tempDir), "infected file must be removed from disk");
        verify(fileRepository, never()).save(any());
    }

    @Test
    void blocksExecutableContentEvenWithInnocentExtension() {
        byte[] peHeader = new byte[]{
                'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00, 0x00, 0x00,
                0x04, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xFF, 0x00, 0x00
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", peHeader);

        assertThrows(FileStorageException.class,
                () -> service.storeFile(file, "user@example.com"));
        verify(fileRepository, never()).save(any());
    }

    @Test
    void loadFileAsResourceRejectsNonOwner() {
        FileEntity entity = storedEntity("owner@example.com");
        when(fileRepository.findByStoredFilename(entity.getStoredFilename()))
                .thenReturn(Optional.of(entity));

        assertThrows(FileStorageException.class,
                () -> service.loadFileAsResource(entity.getStoredFilename(), "intruder@example.com"));
    }

    @Test
    void loadFileAsResourceReturnsResourceForOwnerAndAudits() throws Exception {
        FileEntity entity = storedEntity("user@example.com");
        Path onDisk = tempDir.resolve(entity.getStoredFilename());
        Files.writeString(onDisk, "content");
        when(fileRepository.findByStoredFilename(entity.getStoredFilename()))
                .thenReturn(Optional.of(entity));

        var resource = service.loadFileAsResource(entity.getStoredFilename(), "user@example.com");

        assertTrue(resource.exists());
        verify(userActivityLogRepository).save(any());
    }

    @Test
    void loadFileAsResourceFailsForMissingRow() {
        when(fileRepository.findByStoredFilename("nope.txt")).thenReturn(Optional.empty());
        assertThrows(com.securetransfer.SFTG.exception.ResourceNotFoundException.class,
                () -> service.loadFileAsResource("nope.txt", "user@example.com"));
    }

    @Test
    void loadFileAsResourceRejectsPathTraversal() {
        FileEntity entity = storedEntity("user@example.com");
        entity.setStoredFilename("../escape.txt");
        when(fileRepository.findByStoredFilename("../escape.txt")).thenReturn(Optional.of(entity));

        assertThrows(FileStorageException.class,
                () -> service.loadFileAsResource("../escape.txt", "user@example.com"));
    }

    @Test
    void storeFileFailsForUnknownUploader() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        assertThrows(ResourceNotFoundException.class,
                () -> service.storeFile(file, "ghost@example.com"));
    }

    @Test
    void myFilesIncludesShareStateAndDownloadLogs() {
        FileEntity entity = storedEntity("user@example.com");
        when(fileRepository.findByUploadedBy_Email("user@example.com")).thenReturn(List.of(entity));

        SharedLink link = SharedLink.builder()
                .id(9L)
                .token("share-token")
                .storedFilename(entity.getStoredFilename())
                .originalFilename("hello.txt")
                .createdByUsername("user@example.com")
                .expiresAt(Instant.now().plusSeconds(3600))
                .downloadLimit(5)
                .downloadCount(2)
                .active(true)
                .file(entity)
                .build();
        when(sharedLinkRepository.findByStoredFilename(entity.getStoredFilename()))
                .thenReturn(Optional.of(link));
        DownloadLog log = DownloadLog.builder()
                .downloaderEmail("downloader@example.com")
                .downloadedAt(Instant.now())
                .sharedLink(link)
                .build();
        when(downloadLogRepository.findBySharedLink_Id(9L)).thenReturn(List.of(log));

        List<FileDetailsResponseDTO> result = service.getMyFilesWithDetails("user@example.com");

        assertEquals(1, result.size());
        assertTrue(result.get(0).isActive());
        assertEquals(5, result.get(0).getDownloadLimit());
        assertEquals(2, result.get(0).getDownloadCount());
        assertEquals(1, result.get(0).getDownloadLogs().size());
        assertEquals("downloader@example.com",
                result.get(0).getDownloadLogs().get(0).getDownloaderEmail());
    }

    @Test
    void myFilesWithoutShareLinkReportsInactiveFile() {
        FileEntity entity = storedEntity("user@example.com");
        when(fileRepository.findByUploadedBy_Email("user@example.com")).thenReturn(List.of(entity));
        when(sharedLinkRepository.findByStoredFilename(entity.getStoredFilename()))
                .thenReturn(Optional.empty());

        List<FileDetailsResponseDTO> result = service.getMyFilesWithDetails("user@example.com");

        assertFalse(result.get(0).isActive());
        assertEquals(0, result.get(0).getDownloadLimit());
        assertEquals(0, result.get(0).getDownloadCount());
        assertTrue(result.get(0).getDownloadLogs().isEmpty());
    }

    @Test
    void sharedDownloadReturnsExistingFile() throws Exception {
        FileEntity entity = storedEntity("owner@example.com");
        Files.writeString(tempDir.resolve(entity.getStoredFilename()), "content");
        when(fileRepository.findByStoredFilename(entity.getStoredFilename()))
                .thenReturn(Optional.of(entity));

        assertTrue(service.loadSharedFileAsResource(entity.getStoredFilename()).exists());
    }

    @Test
    void sharedDownloadFailsForMissingRowOrBytes() {
        when(fileRepository.findByStoredFilename("missing.txt")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.loadSharedFileAsResource("missing.txt"));

        FileEntity entity = storedEntity("owner@example.com");
        when(fileRepository.findByStoredFilename(entity.getStoredFilename()))
                .thenReturn(Optional.of(entity));
        assertThrows(ResourceNotFoundException.class,
                () -> service.loadSharedFileAsResource(entity.getStoredFilename()));
    }

    @Test
    void getFileByStoredFilenameFailsWhenMissing() {
        when(fileRepository.findByStoredFilename("gone.txt")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.getFileByStoredFilename("gone.txt"));
    }

    @Test
    void getFileMetadataReturnsOptional() {
        FileEntity entity = storedEntity("user@example.com");
        when(fileRepository.findByStoredFilename(entity.getStoredFilename()))
                .thenReturn(Optional.of(entity));

        assertTrue(service.getFileMetadataByStoredFilename(entity.getStoredFilename()).isPresent());
        assertTrue(service.getFileMetadataByStoredFilename("absent").isEmpty());
    }

    private FileEntity storedEntity(String owner) {
        User u = new User();
        u.setEmail(owner);
        FileEntity entity = new FileEntity();
        entity.setId(1L);
        entity.setOriginalFilename("hello.txt");
        entity.setStoredFilename("uuid_hello.txt");
        entity.setUploadedBy(u);
        entity.setFilePath(tempDir.resolve("uuid_hello.txt").toString());
        return entity;
    }

    private long countFiles(Path dir) {
        try (var stream = Files.list(dir)) {
            return stream.count();
        } catch (Exception e) {
            return -1;
        }
    }
}
