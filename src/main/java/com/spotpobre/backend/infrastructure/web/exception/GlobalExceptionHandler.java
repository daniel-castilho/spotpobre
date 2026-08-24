package com.spotpobre.backend.infrastructure.web.exception;

import com.spotpobre.backend.domain.common.ConflictException;
import com.spotpobre.backend.domain.common.ForbiddenException;
import com.spotpobre.backend.domain.common.IdempotencyConflictException;
import com.spotpobre.backend.domain.common.IdempotencyInProgressException;
import com.spotpobre.backend.domain.common.IdempotencyLeaseLostException;
import com.spotpobre.backend.domain.common.NotFoundException;
import com.spotpobre.backend.domain.common.PayloadTooLargeException;
import com.spotpobre.backend.domain.common.RateLimiterUnavailableException;
import com.spotpobre.backend.domain.common.UploadIntegrityException;
import com.spotpobre.backend.domain.playlist.model.PlaylistConcurrentModificationException;
import com.spotpobre.backend.infrastructure.web.dto.response.ErrorResponse;
import com.spotpobre.backend.infrastructure.web.filter.RequestSizeLimitFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                validationErrors.put(error.getField(), error.getDefaultMessage()));

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "One or more fields have an error",
                request,
                validationErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        Map<String, String> validationErrors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                validationErrors.put(violation.getPropertyPath().toString(), violation.getMessage()));

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "One or more parameters have an error",
                request,
                validationErrors
        );
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), request, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request, null);
    }

    @ExceptionHandler(PlaylistConcurrentModificationException.class)
    public ResponseEntity<ErrorResponse> handlePlaylistConcurrentModification(
            PlaylistConcurrentModificationException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), request, null);
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<ErrorResponse> handlePayloadTooLarge(PayloadTooLargeException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large", ex.getMessage(), request, null);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException ex, HttpServletRequest request) {
        if (hasCause(ex, RequestSizeLimitFilter.BodyLimitExceededException.class)) {
            return buildResponse(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Payload Too Large",
                    "Request body exceeds the maximum allowed size of 64 KB.",
                    request,
                    null
            );
        }
        return buildResponse(HttpStatus.BAD_REQUEST, "Validation Error", "Malformed request body", request, null);
    }

    private static boolean hasCause(final Throwable throwable, final Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyConflict(
            IdempotencyConflictException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Idempotency Conflict", ex.getMessage(), request, null);
    }

    @ExceptionHandler(IdempotencyInProgressException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyInProgress(
            IdempotencyInProgressException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.CONFLICT,
                "Request In Progress",
                ex.getMessage(),
                request,
                null,
                Map.of("Retry-After", String.valueOf(Math.max(1, ex.getRetryAfterSeconds())))
        );
    }

    @ExceptionHandler(IdempotencyLeaseLostException.class)
    public ResponseEntity<ErrorResponse> handleIdempotencyLeaseLost(
            IdempotencyLeaseLostException ex, HttpServletRequest request) {
        // The business write may exist but was not published by us. A short retry with the
        // same Idempotency-Key replays whoever won the lease — never a duplicate resource.
        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Retry With Same Idempotency Key",
                ex.getMessage(),
                request,
                null,
                Map.of("Retry-After", "1")
        );
    }

    @ExceptionHandler(RateLimiterUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleRateLimiterUnavailable(
            RateLimiterUnavailableException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", ex.getMessage(), request, null);
    }

    @ExceptionHandler(com.spotpobre.backend.domain.common.TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(
            com.spotpobre.backend.domain.common.TooManyRequestsException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests", ex.getMessage(), request, null);
    }

    @ExceptionHandler(UploadIntegrityException.class)
    public ResponseEntity<ErrorResponse> handleUploadIntegrity(UploadIntegrityException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "Upload Integrity Failure", ex.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(IllegalStateException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Business Rule Error", ex.getMessage(), request, null);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Business Rule Error", ex.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation Error",
                "Invalid value for parameter '" + ex.getName() + "'",
                request,
                null
        );
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Invalid username or password",
                request,
                null
        );
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Forbidden", ex.getMessage(), request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return buildResponse(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "You do not have permission to access this resource",
                request,
                null
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, HttpServletRequest request) {
        logger.error("Unexpected error on {} {}: {} - {}",
                request.getMethod(), request.getRequestURI(),
                ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. Please try again later.",
                request,
                null
        );
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            final HttpStatus status,
            final String error,
            final String message,
            final HttpServletRequest request,
            final Map<String, String> validationErrors) {
        return buildResponse(status, error, message, request, validationErrors, Map.of());
    }

    private ResponseEntity<ErrorResponse> buildResponse(
            final HttpStatus status,
            final String error,
            final String message,
            final HttpServletRequest request,
            final Map<String, String> validationErrors,
            final Map<String, String> headers) {
        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now(),
                status.value(),
                error,
                message,
                request.getRequestURI(),
                validationErrors
        );
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        headers.forEach(builder::header);
        return builder.body(errorResponse);
    }
}