package com.securetransfer.SFTG.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenUtilTest {

    private static final String SECRET =
            "dGVzdF9zZWNyZXRfa2V5X3RoYXRfaXNfNjRfYnl0ZXNfbG9uZ19mb3JfaHM1MTI=";

    private JwtTokenUtil jwtTokenUtil;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtTokenUtil = new JwtTokenUtil();
        ReflectionTestUtils.setField(jwtTokenUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtTokenUtil, "expirationMs", 60_000L);
        userDetails = new User("user@example.com", "pw", List.of());
    }

    @Test
    void generatedTokenCarriesUsername() {
        String token = jwtTokenUtil.generateToken(userDetails);
        assertNotNull(token);
        assertEquals("user@example.com", jwtTokenUtil.getUsernameFromToken(token));
    }

    @Test
    void validateTokenAcceptsMatchingUser() {
        String token = jwtTokenUtil.generateToken(userDetails);
        assertTrue(jwtTokenUtil.validateToken(token, userDetails));
    }

    @Test
    void validateTokenRejectsDifferentUser() {
        String token = jwtTokenUtil.generateToken(userDetails);
        UserDetails other = new User("other@example.com", "pw", List.of());
        assertFalse(jwtTokenUtil.validateToken(token, other));
    }

    @Test
    void expiredTokenIsRejected() {
        ReflectionTestUtils.setField(jwtTokenUtil, "expirationMs", -1000L);
        String token = jwtTokenUtil.generateToken(userDetails);
        assertThrows(ExpiredJwtException.class, () -> jwtTokenUtil.getUsernameFromToken(token));
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtTokenUtil.generateToken(userDetails);
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertThrows(JwtException.class, () -> jwtTokenUtil.getUsernameFromToken(tampered));
    }

    @Test
    void expirationDateIsInTheFuture() {
        String token = jwtTokenUtil.generateToken(userDetails);
        assertTrue(jwtTokenUtil.getExpirationDateFromToken(token).after(new java.util.Date()));
    }
}
