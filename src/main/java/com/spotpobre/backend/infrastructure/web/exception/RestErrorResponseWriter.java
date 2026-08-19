package com.spotpobre.backend.infrastructure.web.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spotpobre.backend.infrastructure.web.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Writes the canonical {@link ErrorResponse} envelope to an HTTP response.
 * Shared by the security handlers (entry point / access denied) and the JWT
 * filter so that every error path produces the same JSON shape.
 */
@Component
@RequiredArgsConstructor
public class RestErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final HttpStatus status,
            final String error,
            final String message) throws IOException {
        final ErrorResponse body = new ErrorResponse(
                Instant.now(),
                status.value(),
                error,
                message,
                request.getRequestURI(),
                null
        );
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}