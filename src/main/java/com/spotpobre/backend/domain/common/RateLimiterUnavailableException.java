package com.spotpobre.backend.domain.common;

/**
 * Thrown when the rate limiter backend is unavailable and the endpoint's failure policy
 * is fail-closed. Maps to HTTP 503.
 */
public class RateLimiterUnavailableException extends RuntimeException {

    public RateLimiterUnavailableException(final String message) {
        super(message);
    }
}
