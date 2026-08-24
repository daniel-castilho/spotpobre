package com.spotpobre.backend.domain.common;

/**
 * Thrown when an idempotent operation performed its business write but lost the lease before
 * the result could be durably recorded (another instance took over or replaced the record).
 *
 * <p>The caller must not publish a success response for a result it could not record: the
 * instance that now owns the lease will re-execute and publish. Clients retry with the same
 * {@code Idempotency-Key}; the retry replays the recorded outcome instead of duplicating the
 * resource.</p>
 */
public class IdempotencyLeaseLostException extends RuntimeException {

    public IdempotencyLeaseLostException(final String message) {
        super(message);
    }
}
