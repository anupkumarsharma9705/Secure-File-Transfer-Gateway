package com.securetransfer.SFTG.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securetransfer.SFTG.dto.AuthRequest;
import com.securetransfer.SFTG.dto.AuthResponse;
import com.securetransfer.SFTG.dto.FileDetailsResponseDTO;
import com.securetransfer.SFTG.dto.OtpRequest;
import com.securetransfer.SFTG.dto.RegisterRequest;
import com.securetransfer.SFTG.dto.ShareLinkRequest;
import com.securetransfer.SFTG.exception.ApiExceptionHandler;
import com.securetransfer.SFTG.model.FileEntity;
import com.securetransfer.SFTG.model.Role;
import com.securetransfer.SFTG.model.SharedLink;
import com.securetransfer.SFTG.model.User;
import com.securetransfer.SFTG.repository.DownloadLogRepository;
import com.securetransfer.SFTG.repository.UserActivityLogRepository;
import com.securetransfer.SFTG.repository.UserRepository;
import com.securetransfer.SFTG.security.JwtTokenUtil;
import com.securetransfer.SFTG.service.AuthService;
import com.securetransfer.SFTG.service.FileService;
import com.securetransfer.SFTG.service.FileShareService;
import com.securetransfer.SFTG.service.TokenBlacklistService;
import com.securetransfer.SFTG.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests using standalone MockMvc. Services/repositories are
 * mocked, so these verify routing, status codes, payload shape and the
 * exception -> HTTP mapping in ApiExceptionHandler.
 */
@ExtendWith(MockitoExtension.class)
class ControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private AuthService authService;
    @Mock private UserService userService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private JwtTokenUtil jwtTokenUtil;
    @Mock private UserActivityLogRepository userActivityLogRepository;
    @Mock private FileService fileService;
    @Mock private FileShareService fileShareService;
    @Mock private DownloadLogRepository downloadLogRepository;
    @Mock private UserRepository userRepository;

    private MockMvc authMvc;
    private MockMvc fileMvc;
    private MockMvc shareMvc;
    private MockMvc accountMvc;
    private MockMvc statsMvc;

    private Authentication auth;

    @BeforeEach
    void setUp() {
        AuthController authController = new AuthController(
                userService, authService, tokenBlacklistService, jwtTokenUtil, userActivityLogRepository);
        authMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new ApiExceptionHandler()).build();

        FileController fileController = new FileController();
        ReflectionTestUtils.setField(fileController, "fileService", fileService);
        fileMvc = MockMvcBuilders.standaloneSetup(fileController)
                .setControllerAdvice(new ApiExceptionHandler()).build();

        FileShareController shareController = new FileShareController(
                fileShareService, fileService, downloadLogRepository);
        shareMvc = MockMvcBuilders.standaloneSetup(shareController)
                .setControllerAdvice(new ApiExceptionHandler()).build();

        AccountController accountController = new AccountController(userRepository, userActivityLogRepository);
        accountMvc = MockMvcBuilders.standaloneSetup(accountController)
                .setControllerAdvice(new ApiExceptionHandler()).build();

        StatsController statsController = new StatsController(userActivityLogRepository, userRepository);
        statsMvc = MockMvcBuilders.standaloneSetup(statsController)
                .setControllerAdvice(new ApiExceptionHandler()).build();

        auth = new UsernamePasswordAuthenticationToken(
                "user@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    // ---------------- AuthController ----------------

    @Test
    void registerReturnsConfirmation() throws Exception {
        authMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("new@example.com", "Password1!"))))
                .andExpect(status().isOk())
                .andExpect(content().string("OTP sent to email"));

        verify(userService).register(any(RegisterRequest.class));
    }

    @Test
    void verifyOtpReturnsConfirmation() throws Exception {
        OtpRequest request = new OtpRequest();
        request.setEmail("user@example.com");
        request.setOtp("123456");

        authMvc.perform(post("/api/auth/verify-otp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Email verified successfully"));

        verify(userService).verifyOtp(any(OtpRequest.class));
    }

    @Test
    void loginReturnsJwtToken() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setEmail("user@example.com");
        request.setPassword("Password1!");
        when(authService.login(any(AuthRequest.class), any())).thenReturn("signed-jwt");

        authMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jwtToken").value("signed-jwt"));
    }

    @Test
    void logoutWithoutAuthenticationReturnsUnauthorized() throws Exception {
        authMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("You are not logged in"));
    }

    @Test
    void logoutWithBearerTokenBlacklistsAndAudits() throws Exception {
        when(jwtTokenUtil.getExpirationDateFromToken("jwt-token"))
                .thenReturn(java.util.Date.from(Instant.now().plusSeconds(3600)));

        authMvc.perform(post("/api/auth/logout")
                        .principal(auth)
                        .header("Authorization", "Bearer jwt-token")
                        .header("X-Forwarded-For", "203.0.113.9, 10.0.0.1")
                        .header("User-Agent", "test-agent"))
                .andExpect(status().isOk())
                .andExpect(content().string("Logged out successfully"));

        verify(tokenBlacklistService).blacklistToken(
                org.mockito.ArgumentMatchers.eq("jwt-token"), any(Instant.class),
                org.mockito.ArgumentMatchers.eq("user@example.com"));
        verify(userActivityLogRepository).save(any());
    }

    @Test
    void logoutWithoutBearerHeaderStillAuditsButDoesNotBlacklist() throws Exception {
        authMvc.perform(post("/api/auth/logout").principal(auth))
                .andExpect(status().isOk());

        verify(tokenBlacklistService, never()).blacklistToken(anyString(), any(), anyString());
        verify(userActivityLogRepository).save(any());
    }

    // ---------------- FileController ----------------

    @Test
    void uploadReturnsFileResponse() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "hello.txt", "text/plain", "hello".getBytes());
        User uploader = user("user@example.com");
        FileEntity entity = fileEntity("hello.txt", "u_hello.txt", uploader);
        when(fileService.storeFile(any(), anyString())).thenReturn(entity);

        Principal principal = () -> "user@example.com";

        fileMvc.perform(multipart("/api/files/upload").file(file).principal(principal))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("hello.txt"))
                .andExpect(jsonPath("$.uploadedByUsername").value("user@example.com"));
    }

    @Test
    void downloadReturnsAttachment() throws Exception {
        when(fileService.loadFileAsResource("u_hello.txt", "user@example.com"))
                .thenReturn(namedResource("hello.txt"));

        fileMvc.perform(get("/api/files/download/u_hello.txt").principal(auth))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"hello.txt\""));
    }

    @Test
    void myFilesReturnsFileList() throws Exception {
        when(fileService.getMyFilesWithDetails("user@example.com")).thenReturn(List.of(
                FileDetailsResponseDTO.builder()
                        .id(1L).originalFilename("hello.txt").fileSize(5L)
                        .uploadDate(Instant.now()).expiryAt(Instant.now().plusSeconds(60))
                        .active(true).downloadLimit(5).downloadCount(1)
                        .downloadLogs(List.of()).build()));

        fileMvc.perform(get("/api/files/my-files").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].originalFilename").value("hello.txt"))
                .andExpect(jsonPath("$[0].active").value(true));
    }

    // ---------------- FileShareController ----------------

    @Test
    void generateShareLinkReturnsLink() throws Exception {
        ShareLinkRequest request = new ShareLinkRequest();
        request.setStoredFilename(UUID.randomUUID() + "_hello.txt");
        when(fileShareService.generateShareLink(anyString(), anyString()))
                .thenReturn("share-token");

        shareMvc.perform(post("/api/share/generate")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("share-token"));
    }

    @Test
    void downloadSharedFileLogsAndReturnsAttachment() throws Exception {
        SharedLink link = SharedLink.builder()
                .token("token").storedFilename("u_hello.txt")
                .originalFilename("hello.txt").createdByUsername("owner@example.com")
                .expiresAt(Instant.now().plusSeconds(3600))
                .downloadLimit(5).downloadCount(0).active(true).build();

        FileEntity entity = fileEntity("hello.txt", "u_hello.txt", user("owner@example.com"));
        when(fileShareService.validateLink("token")).thenReturn(link);
        when(fileService.getFileByStoredFilename("u_hello.txt")).thenReturn(entity);
        when(fileService.loadSharedFileAsResource("u_hello.txt")).thenReturn(namedResource("hello.txt"));

        shareMvc.perform(get("/api/share/download/token").principal(auth))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"hello.txt\""));

        verify(downloadLogRepository).save(any());
    }

    // ---------------- AccountController ----------------

    @Test
    void profileReturnsEmailRoleAndHistory() throws Exception {
        User user = user("user@example.com");
        user.setRoles(Set.of(Role.ROLE_USER));
        user.setCreatedAt(Instant.now());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userActivityLogRepository.findAllByUserEmailOrderByTimestampDesc("user@example.com"))
                .thenReturn(List.of());

        accountMvc.perform(get("/api/account/me").principal(auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.logs").isArray());
    }

    @Test
    void profileReturns404ForMissingUser() throws Exception {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());

        accountMvc.perform(get("/api/account/me").principal(auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // ---------------- StatsController ----------------

    @Test
    void globalStatsReturnsCounts() throws Exception {
        when(userRepository.count()).thenReturn(10L);
        when(userActivityLogRepository.countTotalUploads()).thenReturn(3L);
        when(userActivityLogRepository.countTotalDownloads()).thenReturn(7L);

        statsMvc.perform(get("/api/stats/global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uniqueVisitors").value(10))
                .andExpect(jsonPath("$.totalUploads").value(3))
                .andExpect(jsonPath("$.totalDownloads").value(7));
    }

    @Test
    void healthReturnsOk() throws Exception {
        statsMvc.perform(get("/api/stats/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        return user;
    }

    private FileEntity fileEntity(String original, String stored, User uploader) {
        FileEntity entity = new FileEntity();
        entity.setId(1L);
        entity.setOriginalFilename(original);
        entity.setStoredFilename(stored);
        entity.setMimeType("text/plain");
        entity.setFileSize(5L);
        entity.setUploadDate(Instant.now());
        entity.setExpiryAt(Instant.now().plusSeconds(3600));
        entity.setUploadedBy(uploader);
        entity.setFilePath("target/" + stored);
        return entity;
    }

    private Resource namedResource(String filename) {
        return new ByteArrayResource("data".getBytes()) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }
}
