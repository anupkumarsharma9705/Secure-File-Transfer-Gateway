package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.dto.OtpRequest;
import com.securetransfer.SFTG.dto.RegisterRequest;
import com.securetransfer.SFTG.exception.AccountLockedException;
import com.securetransfer.SFTG.model.Role;
import com.securetransfer.SFTG.model.User;
import com.securetransfer.SFTG.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private UserService service;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
    }

    @Test
    void registerCreatesUnverifiedUserWithOtp() {
        RegisterRequest request = new RegisterRequest("new@example.com", "Password1!");
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = service.register(request);

        assertFalse(user.isEmailVerified());
        assertEquals("new@example.com", user.getEmail());
        assertTrue(encoder.matches("Password1!", user.getPassword()));
        assertNotNull(user.getOtp());
        assertEquals(6, user.getOtp().length());
        assertTrue(user.getRoles().contains(Role.ROLE_USER));
        assertNotNull(user.getCreatedAt());
        assertTrue(user.isActive());
        verify(emailService).sendOtp(eq("new@example.com"), anyString());
    }

    @Test
    void registerRejectsExistingVerifiedAccount() {
        User existing = new User();
        existing.setEmail("taken@example.com");
        existing.setEmailVerified(true);
        when(userRepository.findByEmail("taken@example.com")).thenReturn(Optional.of(existing));

        assertThrows(RuntimeException.class,
                () -> service.register(new RegisterRequest("taken@example.com", "Password1!")));
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerResendsOtpForUnverifiedAccount() {
        User existing = new User();
        existing.setEmail("pending@example.com");
        existing.setEmailVerified(false);
        existing.setOtpAttempts(4);
        when(userRepository.findByEmail("pending@example.com")).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User user = service.register(new RegisterRequest("pending@example.com", "Password1!"));

        assertEquals(0, user.getOtpAttempts(), "fresh OTP must reset the attempt budget");
        assertNotNull(user.getOtp());
        verify(emailService).sendOtp(eq("pending@example.com"), anyString());
    }

    @Test
    void verifyOtpMarksEmailVerifiedAndClearsCode() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setEmailVerified(false);
        user.setOtp("123456");
        user.setOtpExpiryTime(System.currentTimeMillis() + 60_000);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        service.verifyOtp(otp("user@example.com", "123456"));

        assertTrue(user.isEmailVerified());
        assertNull(user.getOtp());
        assertNull(user.getOtpExpiryTime());
        verify(userRepository).save(user);
    }

    @Test
    void wrongOtpIncrementsAttempts() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setOtp("123456");
        user.setOtpExpiryTime(System.currentTimeMillis() + 60_000);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class,
                () -> service.verifyOtp(otp("user@example.com", "000000")));
        assertEquals(1, user.getOtpAttempts());
    }

    @Test
    void fifthWrongOtpBurnsTheCode() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setOtp("123456");
        user.setOtpAttempts(4);
        user.setOtpExpiryTime(System.currentTimeMillis() + 60_000);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThrows(AccountLockedException.class,
                () -> service.verifyOtp(otp("user@example.com", "000000")));
        assertNull(user.getOtp());
        assertNull(user.getOtpExpiryTime());
    }

    @Test
    void expiredOtpIsRejected() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setOtp("123456");
        user.setOtpExpiryTime(System.currentTimeMillis() - 1000);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class,
                () -> service.verifyOtp(otp("user@example.com", "123456")));
        assertFalse(user.isEmailVerified());
    }

    @Test
    void verifyOtpRejectsAlreadyVerifiedAccount() {
        User user = new User();
        user.setEmail("user@example.com");
        user.setEmailVerified(true);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThrows(RuntimeException.class,
                () -> service.verifyOtp(otp("user@example.com", "123456")));
    }

    private OtpRequest otp(String email, String code) {
        OtpRequest request = new OtpRequest();
        request.setEmail(email);
        request.setOtp(code);
        return request;
    }
}
