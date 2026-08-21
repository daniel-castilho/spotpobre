package com.spotpobre.backend.domain.common;

/**
 * Thrown when a declared or observed payload exceeds the protocol size limit.
 * Maps to HTTP 413.
 */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(final String message) {
        super(message);
    }
}
