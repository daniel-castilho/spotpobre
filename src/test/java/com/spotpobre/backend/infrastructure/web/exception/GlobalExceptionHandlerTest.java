package com.spotpobre.backend.infrastructure.web.exception;

import com.spotpobre.backend.infrastructure.web.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    @Mock
    private HttpServletRequest request;

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleIllegalStateException_shouldReturn400() {
        when(request.getRequestURI()).thenReturn("/api/v1/playlists");

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(
                new IllegalStateException("User cannot have more than 10 playlists."), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Business Rule Error", response.getBody().error());
        assertEquals("User cannot have more than 10 playlists.", response.getBody().message());
    }

    @Test
    void handleIllegalArgumentException_shouldReturn400() {
        when(request.getRequestURI()).thenReturn("/api/v1/albums");

        ResponseEntity<ErrorResponse> response = handler.handleIllegalArgument(
                new IllegalArgumentException("Artist not found: abc"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Artist not found: abc", response.getBody().message());
    }

    @Test
    void handleBadCredentials_shouldReturn401WithoutLeakingExistence() {
        when(request.getRequestURI()).thenReturn("/api/v1/auth/authenticate");

        ResponseEntity<ErrorResponse> response = handler.handleBadCredentials(
                new BadCredentialsException("Bad credentials"), request);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Invalid username or password", response.getBody().message());
    }

    @Test
    void handleAccessDenied_shouldReturn403() {
        when(request.getRequestURI()).thenReturn("/api/v1/artists");

        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(
                new AccessDeniedException("Access denied"), request);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Forbidden", response.getBody().error());
    }

    @Test
    void handleGenericException_shouldReturn500() {
        when(request.getRequestURI()).thenReturn("/api/v1/songs");

        ResponseEntity<ErrorResponse> response = handler.handleGenericException(
                new RuntimeException("boom"), request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Internal Server Error", response.getBody().error());
    }
}