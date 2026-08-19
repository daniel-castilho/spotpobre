package com.spotpobre.backend.domain.common;

/**
 * Thrown when a requested resource does not exist
 * (for example, a playlist, song, album or artist that cannot be found).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(final String message) {
        super(message);
    }
}