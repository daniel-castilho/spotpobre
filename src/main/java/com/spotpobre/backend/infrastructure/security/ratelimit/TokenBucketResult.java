package com.spotpobre.backend.infrastructure.security.ratelimit;

import java.time.Duration;

/**
 * Result of one token-bucket acquisition attempt for a single bucket.
 *
 * @param allowed       whether the request may proceed through this bucket
 * @param remaining     whole tokens left after this attempt (0 when blocked)
 * @param resetSeconds  seconds until a blocked bucket has a full token again (useful refill)
 */
public record TokenBucketResult(boolean allowed, long remaining, long resetSeconds) {

    public static TokenBucketResult allowed(final long remaining) {
        return new TokenBucketResult(true, remaining, 0L);
    }

    public static TokenBucketResult blocked(final long resetSeconds) {
        return new TokenBucketResult(false, 0L, Math.max(1, resetSeconds));
    }

    public Duration retryAfter() {
        return Duration.ofSeconds(resetSeconds);
    }
}
