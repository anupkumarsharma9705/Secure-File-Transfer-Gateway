package com.securetransfer.SFTG.integration;

import com.securetransfer.SFTG.dto.AuthResponse;
import com.securetransfer.SFTG.dto.OtpRequest;
import com.securetransfer.SFTG.dto.RegisterRequest;
import com.securetransfer.SFTG.model.FileEntity;
import com.securetransfer.SFTG.model.SharedLink;
import com.securetransfer.SFTG.model.User;
import com.securetransfer.SFTG.repository.FileRepository;
import com.securetransfer.SFTG.repository.SharedLinkRepository;
import com.securetransfer.SFTG.repository.UserRepository;
import com.securetransfer.SFTG.service.VirusTotalService;
import com.securetransfer.SFTG.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

/**
 * Full-stack integration tests against a real MySQL (Testcontainers).
 * VirusTotal and the email provider are mocked: they are external services,
 * and their own behavior is covered by VirusTotalService/EmailService unit
 * tests. Skipped automatically when Docker is unavailable.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthAndShareIntegrationTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("SFTG")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private UserRepository userRepository;
    @Autowired private FileRepository fileRepository;
    @Autowired private SharedLinkRepository sharedLinkRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @MockBean private EmailService emailService;
    @MockBean private VirusTotalService virusTotalService;

    @Test
    void registrationVerificationAndLoginFlow() {
        String email = uniqueEmail();
        doNothing().when(emailService).sendOtp(anyString(), anyString());

        ResponseEntity<String> register = rest.postForEntity(
                "/api/auth/register",
                new RegisterRequest(email, "Password1!"),
                String.class);
        assertEquals(HttpStatus.OK, register.getStatusCode());

        ArgumentCaptor<String> otp = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtp(org.mockito.ArgumentMatchers.eq(email), otp.capture());

        ResponseEntity<String> verify = rest.postForEntity(
                "/api/auth/verify-otp",
                otpRequest(email, otp.getValue()),
                String.class);
        assertEquals(HttpStatus.OK, verify.getStatusCode());

        ResponseEntity<AuthResponse> login = rest.postForEntity(
                "/api/auth/login",
                authRequest(email, "Password1!"),
                AuthResponse.class);
        assertEquals(HttpStatus.OK, login.getStatusCode());
        assertNotNull(login.getBody());
        assertFalse(login.getBody().getJwtToken().isBlank());

        User persisted = userRepository.findByEmail(email).orElseThrow();
        assertTrue(persisted.isEmailVerified());
    }

    @Test
    void registrationRejectsWeakPassword() {
        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/register",
                new RegisterRequest(uniqueEmail(), "weak"),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void protectedEndpointRejectsAnonymousRequest() {
        ResponseEntity<String> response = rest.getForEntity("/api/files/my-files", String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() {
        String email = createVerifiedUser();
        ResponseEntity<String> response = rest.postForEntity(
                "/api/auth/login",
                authRequest(email, "WrongPassword1!"),
                String.class);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    @Test
    void healthEndpointIsPublic() {
        ResponseEntity<String> response = rest.getForEntity("/api/stats/health", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("OK", response.getBody());
    }

    @Test
    void actuatorHealthIsPublic() {
        ResponseEntity<String> response = rest.getForEntity("/actuator/health/liveness", String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("UP"));
    }

    @Test
    void expiredShareLinkReturnsGone() {
        String email = createVerifiedUser();
        String jwt = login(email);
        FileEntity file = createFile(email, "expired.txt");
        String token = UUID.randomUUID().toString();
        sharedLinkRepository.save(SharedLink.builder()
                .token(token)
                .storedFilename(file.getStoredFilename())
                .originalFilename(file.getOriginalFilename())
                .createdByUsername(email)
                .expiresAt(Instant.now().minusSeconds(60))
                .downloadLimit(5)
                .downloadCount(0)
                .active(true)
                .file(file)
                .build());

        ResponseEntity<String> response = rest.exchange(
                "/api/share/download/" + token,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(jwt)),
                String.class);

        assertEquals(HttpStatus.GONE, response.getStatusCode());
    }

    @Test
    void exhaustedShareLinkReturnsForbidden() {
        String email = createVerifiedUser();
        String jwt = login(email);
        FileEntity file = createFile(email, "limit.txt");
        String token = UUID.randomUUID().toString();
        sharedLinkRepository.save(SharedLink.builder()
                .token(token)
                .storedFilename(file.getStoredFilename())
                .originalFilename(file.getOriginalFilename())
                .createdByUsername(email)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .downloadLimit(5)
                .downloadCount(5)
                .active(true)
                .file(file)
                .build());

        ResponseEntity<String> response = rest.exchange(
                "/api/share/download/" + token,
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(jwt)),
                String.class);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void malformedShareTokenReturnsBadRequest() {
        String email = createVerifiedUser();
        String jwt = login(email);

        ResponseEntity<String> response = rest.exchange(
                "/api/share/download/not-a-token",
                HttpMethod.GET,
                new HttpEntity<>(authHeaders(jwt)),
                String.class);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    private String uniqueEmail() {
        return "it-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private String createVerifiedUser() {
        String email = uniqueEmail();
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Password1!"));
        user.setEmailVerified(true);
        user.setCreatedAt(Instant.now());
        user.setActive(true);
        user.setRoles(java.util.Set.of(com.securetransfer.SFTG.model.Role.ROLE_USER));
        userRepository.save(user);
        return email;
    }

    private String login(String email) {
        ResponseEntity<AuthResponse> response = rest.postForEntity(
                "/api/auth/login",
                authRequest(email, "Password1!"),
                AuthResponse.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().getJwtToken();
    }

    private com.securetransfer.SFTG.dto.AuthRequest authRequest(String email, String password) {
        com.securetransfer.SFTG.dto.AuthRequest request = new com.securetransfer.SFTG.dto.AuthRequest();
        request.setEmail(email);
        request.setPassword(password);
        return request;
    }

    private OtpRequest otpRequest(String email, String otp) {
        OtpRequest request = new OtpRequest();
        request.setEmail(email);
        request.setOtp(otp);
        return request;
    }

    private FileEntity createFile(String ownerEmail, String name) {
        User owner = userRepository.findByEmail(ownerEmail).orElseThrow();
        FileEntity file = new FileEntity();
        file.setOriginalFilename(name);
        file.setStoredFilename(UUID.randomUUID() + "_" + name);
        file.setMimeType("text/plain");
        file.setFileSize(4L);
        file.setUploadDate(Instant.now());
        file.setExpiryAt(Instant.now().plus(1, ChronoUnit.HOURS));
        file.setUploadedBy(owner);
        file.setFilePath("target/test-uploads/" + file.getStoredFilename());
        return fileRepository.save(file);
    }

    private HttpHeaders authHeaders(String jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        return headers;
    }
}
