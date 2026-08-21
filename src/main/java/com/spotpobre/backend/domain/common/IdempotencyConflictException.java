package com.spotpobre.backend.domain.common;

/**
 * Thrown when an {@code Idempotency-Key} is reused with a different canonical request.
 * Maps to HTTP 409.
 */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException(final String message) {
        super(message);
    }
}
