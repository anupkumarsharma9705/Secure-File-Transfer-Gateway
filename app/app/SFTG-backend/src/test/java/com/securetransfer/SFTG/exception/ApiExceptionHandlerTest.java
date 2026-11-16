package com.securetransfer.SFTG.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsUploadSizeToPayloadTooLarge() {
        assertStatus(HttpStatus.PAYLOAD_TOO_LARGE,
                handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1)));
    }

    @Test
    void mapsNotFound() {
        assertStatus(HttpStatus.NOT_FOUND,
                handler.handleNotFound(new ResourceNotFoundException("missing")));
    }

    @Test
    void mapsForbidden() {
        assertStatus(HttpStatus.FORBIDDEN,
                handler.handleForbidden(new ForbiddenException("nope")));
    }

    @Test
    void mapsExpiredLinkToGone() {
        assertStatus(HttpStatus.GONE,
                handler.handleLinkExpired(new LinkExpiredException("expired")));
    }

    @Test
    void mapsIllegalArgumentToBadRequest() {
        assertStatus(HttpStatus.BAD_REQUEST,
                handler.handleBadRequest(new IllegalArgumentException("bad")));
    }

    @Test
    void mapsBadCredentialsToUnauthorized() {
        assertStatus(HttpStatus.UNAUTHORIZED,
                handler.handleUnauthorized(new BadCredentialsException("bad credentials")));
    }

    @Test
    void mapsFileStorageErrorToInternalServerError() {
        assertStatus(HttpStatus.INTERNAL_SERVER_ERROR,
                handler.handleFileError(new FileStorageException("disk")));
    }

    @Test
    void mapsAccountLocked() {
        assertStatus(HttpStatus.LOCKED,
                handler.handleAccountLocked(new AccountLockedException("locked")));
    }

    @Test
    void mapsValidationErrorToFirstFieldMessage() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "password", "too weak"));

        ResponseEntity<Object> response = handler.handleValidationErrors(
                new MethodArgumentNotValidException(null, binding));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Validation Error", body.get("error"));
        assertTrue(String.valueOf(body.get("message")).contains("password: too weak"));
    }

    @Test
    void mapsGenericExceptionToInternalServerErrorWithoutLeakingDetails() {
        ResponseEntity<Object> response = handler.handleGeneric(new RuntimeException("secret db detail"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("An unexpected error occurred. Please try again later.", body.get("message"));
    }

    private void assertStatus(HttpStatus expected, ResponseEntity<Object> response) {
        assertEquals(expected, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(expected.value(), body.get("status"));
    }
}
