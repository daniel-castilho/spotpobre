package com.spotpobre.backend.domain.common;

/**
 * Thrown when an idempotent operation is still being processed under an active lease.
 * Maps to HTTP 409 with a {@code Retry-After} header.
 */
public class IdempotencyInProgressException extends RuntimeException {

    private final long retryAfterSeconds;

    public IdempotencyInProgressException(final String message, final long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Suggested client retry interval in seconds; never negative.
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
