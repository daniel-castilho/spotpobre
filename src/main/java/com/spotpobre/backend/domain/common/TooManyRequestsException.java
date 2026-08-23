package com.spotpobre.backend.domain.common;

/**
 * Rate-limit style rejection at the application layer (e.g. resend cooldowns). Mapped to
 * HTTP 429 by the global exception handler.
 */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(final String message) {
        super(message);
    }
}
