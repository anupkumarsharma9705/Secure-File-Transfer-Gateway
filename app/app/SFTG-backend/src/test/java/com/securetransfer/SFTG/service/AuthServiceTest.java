package com.securetransfer.SFTG.service;

import com.securetransfer.SFTG.dto.AuthRequest;
import com.securetransfer.SFTG.exception.AccountLockedException;
import com.securetransfer.SFTG.exception.ForbiddenException;
import com.securetransfer.SFTG.model.User;
import com.securetransfer.SFTG.repository.UserActivityLogRepository;
import com.securetransfer.SFTG.repository.UserRepository;
import com.securetransfer.SFTG.security.JwtTokenUtil;
import com.securetransfer.SFTG.security.UserDetailsServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDetailsServiceImpl userDetailsService;

    @Mock
    private JwtTokenUtil jwtTokenUtil;

    @Mock
    private UserActivityLogRepository userActivityLogRepository;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private AuthService service;

    private User user;
    private AuthRequest request;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("user@example.com");
        user.setEmailVerified(true);

        request = new AuthRequest();
        request.setEmail("user@example.com");
        request.setPassword("Password1!");
    }

    @Test
    void loginFailsForUnknownEmail() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> service.login(request, httpRequest));
    }

    @Test
    void loginFailsWhenAccountLocked() {
        user.setAccountLockedUntil(Instant.now().plusSeconds(600));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThrows(AccountLockedException.class, () -> service.login(request, httpRequest));
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void wrongPasswordIncrementsFailureCounter() {
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThrows(BadCredentialsException.class, () -> service.login(request, httpRequest));
        assertEquals(1, user.getFailedLoginAttempts());
        verify(userRepository).save(user);
    }

    @Test
    void fifthFailedAttemptLocksAccount() {
        user.setFailedLoginAttempts(4);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThrows(AccountLockedException.class, () -> service.login(request, httpRequest));
        assertNotNull(user.getAccountLockedUntil());
        assertTrue(user.getAccountLockedUntil().isAfter(Instant.now()));
        verify(userRepository).save(user);
    }

    @Test
    void loginFailsForUnverifiedEmailAfterCorrectPassword() {
        user.setEmailVerified(false);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);

        assertThrows(ForbiddenException.class, () -> service.login(request, httpRequest));
    }

    @Test
    void successfulLoginReturnsTokenAndAuditsRequest() {
        UserDetails userDetails = org.springframework.security.core.userdetails.User
                .withUsername("user@example.com").password("pw").authorities("ROLE_USER").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(userDetails);
        when(jwtTokenUtil.generateToken(userDetails)).thenReturn("signed-jwt");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.7, 10.0.0.1");
        when(httpRequest.getHeader("User-Agent")).thenReturn("test-agent");

        String token = service.login(request, httpRequest);

        assertEquals("signed-jwt", token);
        ArgumentCaptor<com.securetransfer.SFTG.model.UserActivityLog> captor =
                ArgumentCaptor.forClass(com.securetransfer.SFTG.model.UserActivityLog.class);
        verify(userActivityLogRepository).save(captor.capture());
        assertEquals("LOGIN", captor.getValue().getAction());
        assertEquals("203.0.113.7", captor.getValue().getIpAddress());
        assertEquals("test-agent", captor.getValue().getUserAgent());
    }
}
