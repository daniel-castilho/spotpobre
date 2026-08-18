package com.spotpobre.backend.domain.common;

/**
 * Thrown when an authenticated user is not allowed to perform an operation
 * (for example, mutating a playlist they do not own).
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(final String message) {
        super(message);
    }
}
