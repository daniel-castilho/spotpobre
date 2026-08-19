package com.spotpobre.backend.domain.common;

/**
 * Thrown when an operation cannot be completed because it conflicts with the
 * current state of a resource (for example, a duplicate email at registration
 * or exceeding the per-user playlist limit).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(final String message) {
        super(message);
    }
}